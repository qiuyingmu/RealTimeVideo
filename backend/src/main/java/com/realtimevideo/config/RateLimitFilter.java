package com.realtimevideo.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 请求频率限制过滤器
 * 基于客户端IP的限流，防止暴力破解和DDoS攻击
 *
 * 注意：排除路径不会触发限流，但不会放过已认证的请求。
 * 视频流请求通过 EZUIKit 直接连接萤石云，不经过此过滤器。
 */
@Component
@Order(1)
public class RateLimitFilter implements Filter {

    /** 不进行限流的路径前缀 */
    private static final List<String> EXCLUDED_PATHS = List.of(
            "/h2-console",
            "/error"
    );

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();

    private Bucket createNewBucket() {
        // 通用API：每分钟120次（原60次，提高至120以支持多个通道同时加载）
        Bandwidth limit = Bandwidth.classic(120, Refill.greedy(120, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createLoginBucket() {
        // 登录接口：每分钟10次，每小时30次
        Bandwidth perMinute = Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1)));
        Bandwidth perHour = Bandwidth.classic(30, Refill.greedy(30, Duration.ofHours(1)));
        return Bucket.builder()
                .addLimit(perMinute)
                .addLimit(perHour)
                .build();
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String requestPath = request.getRequestURI();

        // 排除路径不进行限流
        for (String exclude : EXCLUDED_PATHS) {
            if (requestPath.startsWith(exclude)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        String clientIp = getClientIp(request);

        Bucket bucket;
        if (requestPath.contains("/api/auth/login")) {
            bucket = loginBuckets.computeIfAbsent(clientIp, k -> createLoginBucket());
        } else if (requestPath.startsWith("/api/")) {
            bucket = buckets.computeIfAbsent(clientIp, k -> createNewBucket());
        } else {
            // 非API路径不限制
            filterChain.doFilter(request, response);
            return;
        }

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            String retryAfter = String.valueOf(
                    requestPath.contains("/api/auth/login") ? 6 : 1
            );
            response.setHeader("Retry-After", retryAfter);
            response.getWriter().write(
                    "{\"success\":false,\"message\":\"请求过于频繁，请稍后再试\",\"retryAfter\":" + retryAfter + "}"
            );
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
