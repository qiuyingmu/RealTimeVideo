package com.realtimevideo.controller;

import com.realtimevideo.dto.ApiResponse;
import com.realtimevideo.dto.CreateUserRequest;
import com.realtimevideo.model.OperationLog;
import com.realtimevideo.model.User;
import com.realtimevideo.model.UserDevicePermission;
import com.realtimevideo.repository.UserDevicePermissionRepository;
import com.realtimevideo.service.AuditLogService;
import com.realtimevideo.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserService userService;
    private final AuditLogService auditLogService;
    private final UserDevicePermissionRepository permissionRepository;

    // ======================== 用户管理 ========================

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<User>>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<ApiResponse<User>> getUserById(@PathVariable Long id) {
        User user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @PostMapping("/users")
    public ResponseEntity<ApiResponse<User>> createUser(
            @Valid @RequestBody CreateUserRequest request, HttpServletRequest httpRequest) {
        User user = userService.createUser(request);
        auditLogService.log("CREATE_USER", "user:" + user.getUsername(),
                "创建用户: " + user.getUsername(), httpRequest);
        return ResponseEntity.ok(ApiResponse.success("用户创建成功", user));
    }

    @PutMapping("/users/{id}/toggle-enabled")
    public ResponseEntity<ApiResponse<User>> toggleUserEnabled(
            @PathVariable Long id, HttpServletRequest httpRequest) {
        User user = userService.toggleUserEnabled(id);
        String status = user.isEnabled() ? "已启用" : "已禁用";
        auditLogService.log("USER_TOGGLE", "user:" + user.getUsername(),
                status + "用户: " + user.getUsername(), httpRequest);
        return ResponseEntity.ok(ApiResponse.success("用户" + status, user));
    }

    @PutMapping("/users/{id}/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 18) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("新密码长度需在6-18位之间"));
        }
        userService.resetPassword(id, newPassword);
        User user = userService.getUserById(id);
        auditLogService.log("RESET_PASSWORD", "user:" + user.getUsername(),
                "重置密码: " + user.getUsername(), httpRequest);
        return ResponseEntity.ok(ApiResponse.success("密码重置成功", null));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable Long id, HttpServletRequest httpRequest) {
        User user = userService.getUserById(id);
        auditLogService.log("DELETE_USER", "user:" + user.getUsername(),
                "删除用户: " + user.getUsername(), httpRequest);
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("用户已删除", null));
    }

    // ======================== 设备权限管理 ========================

    /**
     * 获取所有权限配置（含用户和已授权设备信息）
     */
    @GetMapping("/permissions")
    public ResponseEntity<ApiResponse<List<UserDevicePermission>>> getAllPermissions() {
        List<UserDevicePermission> permissions = permissionRepository.findAll(
                Sort.by(Sort.Direction.ASC, "userId", "deviceSerial"));
        return ResponseEntity.ok(ApiResponse.success(permissions));
    }

    /**
     * 查询某用户的设备权限
     */
    @GetMapping("/permissions/user/{userId}")
    public ResponseEntity<ApiResponse<List<UserDevicePermission>>> getUserPermissions(
            @PathVariable Long userId) {
        List<UserDevicePermission> permissions =
                permissionRepository.findByUserIdOrderByDeviceSerial(userId);
        return ResponseEntity.ok(ApiResponse.success(permissions));
    }

    /**
     * 批量设置用户的设备权限
     * body: {"deviceSerials": ["serial1", "serial2"]}
     */
    @PutMapping("/permissions/user/{userId}")
    public ResponseEntity<ApiResponse<List<UserDevicePermission>>> setUserPermissions(
            @PathVariable Long userId,
            @RequestBody Map<String, List<String>> body,
            HttpServletRequest httpRequest) {
        List<String> deviceSerials = body.get("deviceSerials");
        if (deviceSerials == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("deviceSerials 不能为空"));
        }

        // 获取用户信息
        User user = userService.getUserById(userId);

        // 先清除旧权限
        permissionRepository.deleteByUserId(userId);

        // 批量添加新权限
        List<UserDevicePermission> newPermissions = deviceSerials.stream()
                .map(serial -> UserDevicePermission.builder()
                        .userId(userId)
                        .username(user.getUsername())
                        .deviceSerial(serial)
                        .deviceName("") // 暂不填充，前端会展示
                        .build())
                .collect(Collectors.toList());
        permissionRepository.saveAll(newPermissions);

        auditLogService.log("UPDATE_PERMISSION", "user:" + user.getUsername(),
                "更新设备权限: " + String.join(", ", deviceSerials), httpRequest);

        return ResponseEntity.ok(ApiResponse.success("权限更新成功",
                permissionRepository.findByUserIdOrderByDeviceSerial(userId)));
    }

    /**
     * 移除用户某个设备的权限
     */
    @DeleteMapping("/permissions/user/{userId}/device/{deviceSerial}")
    public ResponseEntity<ApiResponse<Void>> removeDevicePermission(
            @PathVariable Long userId,
            @PathVariable String deviceSerial,
            HttpServletRequest httpRequest) {
        User user = userService.getUserById(userId);
        permissionRepository.deleteByUserIdAndDeviceSerial(userId, deviceSerial);
        auditLogService.log("REMOVE_PERMISSION", "user:" + user.getUsername(),
                "移除设备权限: " + deviceSerial, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("权限已移除", null));
    }

    // ======================== 操作日志查询 ========================

    /**
     * 分页查询操作日志
     * 参数: page, size, action, username
     */
    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<Page<OperationLog>>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String username) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<OperationLog> logs = auditLogService.getLogs(pageRequest, action, username);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
}
