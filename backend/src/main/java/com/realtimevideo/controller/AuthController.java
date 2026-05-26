package com.realtimevideo.controller;

import com.realtimevideo.dto.ApiResponse;
import com.realtimevideo.dto.LoginRequest;
import com.realtimevideo.dto.LoginResponse;
import com.realtimevideo.dto.RefreshTokenRequest;
import com.realtimevideo.service.AuditLogService;
import com.realtimevideo.service.JwtService;
import com.realtimevideo.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        try {
            LoginResponse response = userService.login(request);
            auditLogService.log("LOGIN", "user:" + response.getUsername(),
                    "登录成功", httpRequest);
            return ResponseEntity.ok(ApiResponse.success("登录成功", response));
        } catch (Exception e) {
            auditLogService.log("LOGIN_FAILED", "user:" + request.getUsername(),
                    "登录失败: " + e.getMessage(), httpRequest);
            throw e;
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        LoginResponse response = userService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("令牌刷新成功", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest httpRequest) {
        try {
            String accessToken = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                accessToken = authHeader.substring(7);

                // 记录用户名
                try {
                    String username = jwtService.extractUsername(accessToken);
                    auditLogService.log("LOGOUT", "user:" + username, "用户登出", httpRequest);
                } catch (Exception ignored) {
                }
            }
            String refreshToken = body != null ? body.get("refreshToken") : null;
            userService.logout(accessToken, refreshToken);
        } catch (Exception e) {
            log.warn("登出处理异常: {}", e.getMessage());
        }
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
}
