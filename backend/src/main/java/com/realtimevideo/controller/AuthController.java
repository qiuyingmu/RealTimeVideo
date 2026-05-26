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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

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
        String accessToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        }
        String refreshToken = body != null ? body.get("refreshToken") : null;

        // 记录用户名（从过期 token 中提取）
        if (accessToken != null) {
            try {
                String username = jwtService.extractUsername(accessToken);
                auditLogService.log("LOGOUT", "user:" + username, "用户登出", httpRequest);
            } catch (Exception ignored) {}
        }

        userService.logout(accessToken, refreshToken);
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
