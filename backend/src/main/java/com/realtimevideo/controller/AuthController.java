package com.realtimevideo.controller;

import com.realtimevideo.dto.ApiResponse;
import com.realtimevideo.dto.LoginRequest;
import com.realtimevideo.dto.LoginResponse;
import com.realtimevideo.service.AuditLogService;
import com.realtimevideo.service.JwtService;
import com.realtimevideo.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final AuditLogService auditLogService;
    private final JwtService jwtService;

    @Value("${jwt.refresh-token-expiration:604800000}")
    private long refreshTokenExpiration;

    private static final String REFRESH_COOKIE_NAME = "refresh_token";

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            LoginResponse response = userService.login(request);
            auditLogService.log("LOGIN", "user:" + response.getUsername(),
                    "登录成功", httpRequest);
            // refreshToken 通过 httpOnly Cookie 下发，不写入 JSON 响应体
            addRefreshTokenCookie(httpResponse, response.getRefreshToken());
            return ResponseEntity.ok(ApiResponse.success("登录成功", response));
        } catch (Exception e) {
            auditLogService.log("LOGIN_FAILED", "user:" + request.getUsername(),
                    "登录失败: " + e.getMessage(), httpRequest);
            throw e;
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse httpResponse) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("刷新令牌不存在，请重新登录"));
        }
        LoginResponse response = userService.refreshToken(refreshToken);
        // 下发新的 refreshToken cookie（token rotation）
        addRefreshTokenCookie(httpResponse, response.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("令牌刷新成功", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String authHeader,
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            String accessToken = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                accessToken = authHeader.substring(7);

                try {
                    String username = jwtService.extractUsername(accessToken);
                    auditLogService.log("LOGOUT", "user:" + username, "用户登出", httpRequest);
                } catch (Exception ignored) {
                }
            }
            userService.logout(accessToken, refreshToken);
        } catch (Exception e) {
            log.warn("登出处理异常: {}", e.getMessage());
        }
        // 清除 refreshToken cookie
        clearRefreshTokenCookie(httpResponse);
        return ResponseEntity.ok(ApiResponse.success("登出成功", null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> me() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication();

        Map<String, Object> userInfo = Map.of(
                "username", auth.getName(),
                "role", auth.getAuthorities().stream()
                        .findFirst().map(Object::toString).orElse(""),
                "authenticated", auth.isAuthenticated()
        );

        return ResponseEntity.ok(ApiResponse.success(userInfo));
    }

    // ========== Cookie 工具方法 ==========

    /**
     * 将 refreshToken 设置为 httpOnly Cookie
     *
     * - httpOnly: true — JS 不可读，防止 XSS 窃取
     * - Secure: 开发环境 false，生产环境由 Nginx 或配置决定
     * - SameSite: Strict — 防止 CSRF
     * - Path: /api/auth — 仅 auth 端点携带此 cookie
     * - Max-Age: 与 refreshToken 过期时间一致（毫秒→秒）
     */
    private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // 开发环境不要求 HTTPS；生产环境通过 Nginx 转发时改为 true
        cookie.setAttribute("SameSite", "Strict");
        cookie.setPath("/api/auth");
        cookie.setMaxAge((int) (refreshTokenExpiration / 1000));
        response.addCookie(cookie);
    }

    /**
     * 清除 refreshToken Cookie（登出时调用）
     */
    private void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setAttribute("SameSite", "Strict");
        cookie.setPath("/api/auth");
        cookie.setMaxAge(0); // 立即过期
        response.addCookie(cookie);
    }
}
