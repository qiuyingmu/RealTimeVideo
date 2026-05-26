package com.realtimevideo.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 请求频率限制过滤器 — 基于客户端IP的令牌桶限流
 *
 * 通用 API: 120 次/分钟
 * 登录接口: 10 次/分钟 + 30 次/小时
 */
@Slf4j
@Component
@Order(1)
public class RateLimitFilter implements Filter {

    private static final List<String> EXCLUDED_PATHS = List.of("/h2-console", "/error");

    private final Map<String, Bucket> apiBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();

    private Bucket createApiBucket() {
        Bandwidth limit = Bandwidth.classic(120, Refill.greedy(120, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createLoginBucket() {
        Bandwidth perMinute = Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1)));
        Bandwidth perHour = Bandwidth.classic(30, Refill.greedy(30, Duration.ofHours(1)));
        return Bucket.builder().addLimit(perMinute).addLimit(perHour).build();
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String requestPath = request.getRequestURI();

        for (String exclude : EXCLUDED_PATHS) {
            if (requestPath.startsWith(exclude)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        if (!requestPath.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        boolean isLogin = requestPath.contains("/api/auth/login");
        Bucket bucket = isLogin
                ? loginBuckets.computeIfAbsent(clientIp, k -> createLoginBucket())
                : apiBuckets.computeIfAbsent(clientIp, k -> createApiBucket());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            int retryAfter = isLogin ? 6 : 1;
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.getWriter().write(
                    "{\"success\":false,\"message\":\"请求过于频繁，请稍后再试\",\"retryAfter\":" + retryAfter + "}");
        }
    }

    /**
     * 每 10 分钟清理一次无活跃 tokens 的 bucket（防止内存泄漏）
     */
    @Scheduled(fixedRate = 600_000)
    public void cleanStaleBuckets() {
        int apiBefore = apiBuckets.size();
        apiBuckets.entrySet().removeIf(e -> e.getValue().getAvailableTokens() > 118);
        int loginBefore = loginBuckets.size();
        loginBuckets.entrySet().removeIf(e -> e.getValue().getAvailableTokens() > 8);
        int cleaned = (apiBefore - apiBuckets.size()) + (loginBefore - loginBuckets.size());
        if (cleaned > 0) {
            log.debug("清理了 {} 个闲置的限流桶", cleaned);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) return xRealIp;
        return request.getRemoteAddr();
    }
}
