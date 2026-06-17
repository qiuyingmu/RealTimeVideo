package com.realtimevideo.controller;

import com.realtimevideo.dto.ApiResponse;
import com.realtimevideo.dto.LoginRequest;
import com.realtimevideo.dto.LoginResponse;
import com.realtimevideo.service.AuditLogService;
import com.realtimevideo.service.JwtService;
import com.realtimevideo.service.SmsAuthService;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final SmsAuthService smsAuthService;

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

    /**
     * 修改密码（首次登录强制修改 / 用户自助修改）
     *
     * 验证旧密码 → 设置新密码（6~18 位）→ 签发新 Token
     * 前端需校验 newPassword === confirmPassword
     */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<LoginResponse>> changePassword(
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        String confirmPassword = body.get("confirmPassword");

        // 参数完整性校验
        if (oldPassword == null || newPassword == null || confirmPassword == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("请填写所有密码字段"));
        }

        // 两次密码一致性校验（后端兜底）
        if (!newPassword.equals(confirmPassword)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("两次输入的新密码不一致"));
        }

        LoginResponse response = userService.changePassword(username, oldPassword, newPassword);

        // 旧 refreshToken 失效，下发新 cookie
        addRefreshTokenCookie(httpResponse, response.getRefreshToken());

        auditLogService.log("CHANGE_PASSWORD", "user:" + username,
                "用户修改密码", httpRequest);

        return ResponseEntity.ok(ApiResponse.success("密码修改成功", response));
    }

    /**
     * 设置密码（短信验证码登录后可选）
     *
     * 无需旧密码，仅验证新密码长度（6~18 位）。
     * 短信登录用户通过手机号验证了身份，可直接设置密码。
     */
    @PostMapping("/set-password")
    public ResponseEntity<ApiResponse<LoginResponse>> setPassword(
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        String newPassword = body.get("newPassword");
        String confirmPassword = body.get("confirmPassword");

        // 参数完整性校验
        if (newPassword == null || confirmPassword == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("请填写密码字段"));
        }

        // 两次密码一致性校验
        if (!newPassword.equals(confirmPassword)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("两次输入的新密码不一致"));
        }

        LoginResponse response = userService.setPassword(username, newPassword);

        // 下发新 refreshToken
        addRefreshTokenCookie(httpResponse, response.getRefreshToken());

        auditLogService.log("SET_PASSWORD", "user:" + username,
                "用户设置密码", httpRequest);

        return ResponseEntity.ok(ApiResponse.success("密码设置成功", response));
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

    // ======================== 短信验证码认证 ========================

    /**
     * 发送短信验证码
     *
     * 先校验手机号在数据库中是否存在（需管理员预先授权），
     * 不存在则拒绝发送，引导用户联系管理员。
     */
    @PostMapping("/send-sms-code")
    public ResponseEntity<ApiResponse<String>> sendSmsCode(
            @RequestBody Map<String, String> body) {
        String phone = body.get("phoneNumber");
        if (phone == null || phone.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("手机号不能为空"));
        }

        // 校验手机号是否已在系统中注册（管理员预设）
        if (!userService.existsByUsername(phone)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("该手机号未授权，请联系管理员开通"));
        }

        SmsAuthService.SendResult result = smsAuthService.sendCode(phone);
        if (result.success()) {
            return ResponseEntity.ok(ApiResponse.success("验证码已发送"));
        }
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(result.message() != null ? result.message() : "发送失败"));
    }

    /**
     * 短信验证码登录
     *
     * 手机号作为用户名，验证码通过后返回 JWT Token。
     * 手机号必须是管理员预先授权的用户。
     */
    @PostMapping("/sms-login")
    public ResponseEntity<ApiResponse<LoginResponse>> smsLogin(
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String phone = body.get("phoneNumber");
        String code = body.get("verifyCode");

        if (phone == null || code == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("手机号和验证码不能为空"));
        }

        // 校验手机号是否存在
        if (!userService.existsByUsername(phone)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("该手机号未授权，请联系管理员开通"));
        }

        // 核验验证码
        boolean verified = smsAuthService.checkCode(phone, code);
        if (!verified) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("验证码错误或已过期"));
        }

        try {
            LoginResponse response = userService.loginByPhone(phone);
            auditLogService.log("SMS_LOGIN", "phone:" + phone,
                    "短信验证码登录成功", httpRequest);
            addRefreshTokenCookie(httpResponse, response.getRefreshToken());
            return ResponseEntity.ok(ApiResponse.success("登录成功", response));
        } catch (Exception e) {
            auditLogService.log("SMS_LOGIN_FAILED", "phone:" + phone,
                    "短信登录失败: " + e.getMessage(), httpRequest);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("登录失败，请重试"));
        }
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
