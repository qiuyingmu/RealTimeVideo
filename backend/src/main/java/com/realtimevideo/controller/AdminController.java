package com.realtimevideo.controller;

import com.realtimevideo.dto.ApiResponse;
import com.realtimevideo.dto.CreateUserRequest;
import com.realtimevideo.model.User;
import com.realtimevideo.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserService userService;

    /**
     * 获取所有用户列表
     */
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<User>>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    /**
     * 获取单个用户
     */
    @GetMapping("/users/{id}")
    public ResponseEntity<ApiResponse<User>> getUserById(@PathVariable Long id) {
        User user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    /**
     * 创建新用户（管理员专用）
     */
    @PostMapping("/users")
    public ResponseEntity<ApiResponse<User>> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        User user = userService.createUser(request);
        return ResponseEntity.ok(ApiResponse.success("用户创建成功", user));
    }

    /**
     * 启用/禁用用户
     */
    @PutMapping("/users/{id}/toggle-enabled")
    public ResponseEntity<ApiResponse<User>> toggleUserEnabled(@PathVariable Long id) {
        User user = userService.toggleUserEnabled(id);
        String status = user.isEnabled() ? "已启用" : "已禁用";
        return ResponseEntity.ok(ApiResponse.success("用户" + status, user));
    }

    /**
     * 重置用户密码
     */
    @PutMapping("/users/{id}/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.length() < 8) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("新密码长度不能少于8位"));
        }
        userService.resetPassword(id, newPassword);
        return ResponseEntity.ok(ApiResponse.success("密码重置成功", null));
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("用户已删除", null));
    }
}
