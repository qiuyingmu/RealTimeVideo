package com.realtimevideo.controller;

import com.realtimevideo.dto.ApiResponse;
import com.realtimevideo.model.Channel;
import com.realtimevideo.model.Device;
import com.realtimevideo.repository.UserDevicePermissionRepository;
import com.realtimevideo.service.AuditLogService;
import com.realtimevideo.service.EzvizService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ezviz")
@RequiredArgsConstructor
public class EzvizController {

    private final EzvizService ezvizService;
    private final AuditLogService auditLogService;
    private final UserDevicePermissionRepository permissionRepository;

    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    @GetMapping("/token")
    public ResponseEntity<ApiResponse<Map<String, String>>> getToken() {
        Map<String, String> token = ezvizService.getEzvizToken();
        return ResponseEntity.ok(ApiResponse.success(token));
    }

    @PostMapping("/sync-devices")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<Device>>> syncDevices(HttpServletRequest request) {
        List<Device> devices = ezvizService.syncDevices();
        auditLogService.log("SYNC_DEVICES", null,
                "从萤石云同步设备，共 " + devices.size() + " 台", request);
        return ResponseEntity.ok(ApiResponse.success(
                "同步成功，共 " + devices.size() + " 台设备", devices));
    }

    /**
     * 获取所有通道（摄像头）
     * ADMIN 返回所有通道，普通用户只返回有权限的设备通道
     */
    @GetMapping("/channels")
    public ResponseEntity<ApiResponse<List<Channel>>> getAllChannels(
            Authentication authentication) {
        List<Channel> allChannels = ezvizService.getAllChannels();

        boolean isAdmin = authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(a -> ADMIN_ROLE.equals(a.getAuthority()));

        if (isAdmin) {
            return ResponseEntity.ok(ApiResponse.success(allChannels));
        }

        // 普通用户：只返回有权限的设备的通道
        String username = authentication.getName();
        List<com.realtimevideo.model.UserDevicePermission> permissions =
                permissionRepository.findByUsername(username);

        if (permissions.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }

        Set<String> allowedSerials = permissions.stream()
                .map(com.realtimevideo.model.UserDevicePermission::getDeviceSerial)
                .collect(Collectors.toSet());

        List<Channel> filtered = allChannels.stream()
                .filter(ch -> allowedSerials.contains(ch.getDeviceSerial()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(filtered));
    }

    /**
     * 轻量刷新通道在线状态（所有认证用户可调用）
     *
     * 仅从萤石云获取最新的 status，不修改设备/通道配置数据。
     * 与 syncDevices() 不同——sync 是全量同步（仅管理员），
     * 此接口只刷新状态（速度更快，对所有人开放）。
     */
    @GetMapping("/channels/refresh-status")
    public ResponseEntity<ApiResponse<List<Channel>>> refreshChannelStatus(
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("未认证"));
        }
        List<Channel> channels = ezvizService.refreshChannelsStatus();

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> ADMIN_ROLE.equals(a.getAuthority()));

        if (isAdmin) {
            return ResponseEntity.ok(ApiResponse.success(channels));
        }

        // 普通用户只返回有权限的通道
        String username = authentication.getName();
        List<com.realtimevideo.model.UserDevicePermission> permissions =
                permissionRepository.findByUsername(username);
        if (permissions.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }
        Set<String> allowedSerials = permissions.stream()
                .map(com.realtimevideo.model.UserDevicePermission::getDeviceSerial)
                .collect(Collectors.toSet());
        List<Channel> filtered = channels.stream()
                .filter(ch -> allowedSerials.contains(ch.getDeviceSerial()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(filtered));
    }

    @GetMapping("/devices/{deviceSerial}/channels")
    public ResponseEntity<ApiResponse<List<Channel>>> getDeviceChannels(
            @PathVariable String deviceSerial,
            Authentication authentication) {
        // 检查权限
        if (!hasDevicePermission(deviceSerial, authentication)) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.error("无权访问该设备"));
        }
        List<Channel> channels = ezvizService.getDeviceChannels(deviceSerial);
        return ResponseEntity.ok(ApiResponse.success(channels));
    }

    @PutMapping("/channels/ptz/enable-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Integer>> enableAllPtz() {
        int count = ezvizService.enableAllPtz();
        return ResponseEntity.ok(ApiResponse.success(
                "已启用 " + count + " 个通道的 PTZ 控制", count));
    }

    @PostMapping("/ptz/start")
    public ResponseEntity<ApiResponse<String>> startPtz(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest,
            Authentication authentication) {
        String deviceSerial = (String) request.get("deviceSerial");
        int channelNo = ((Number) request.get("channelNo")).intValue();
        int direction = ((Number) request.get("direction")).intValue();
        int speed = request.containsKey("speed") ? ((Number) request.get("speed")).intValue() : 50;

        ezvizService.startPtz(deviceSerial, channelNo, direction, speed);
        auditLogService.log("PTZ_CONTROL", deviceSerial + "/CH" + channelNo,
                "PTZ 开始: dir=" + direction + " speed=" + speed, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("PTZ 控制已发送", "ok"));
    }

    @PostMapping("/ptz/stop")
    public ResponseEntity<ApiResponse<String>> stopPtz(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest,
            Authentication authentication) {
        String deviceSerial = (String) request.get("deviceSerial");
        int channelNo = ((Number) request.get("channelNo")).intValue();

        ezvizService.stopPtz(deviceSerial, channelNo);
        auditLogService.log("PTZ_CONTROL", deviceSerial + "/CH" + channelNo,
                "PTZ 停止", httpRequest);
        return ResponseEntity.ok(ApiResponse.success("PTZ 停止已发送", "ok"));
    }

    private boolean hasDevicePermission(String deviceSerial, Authentication authentication) {
        if (authentication == null) return false;
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> ADMIN_ROLE.equals(a.getAuthority()));
        if (isAdmin) return true;
        String username = authentication.getName();
        return permissionRepository.findByUsername(username).stream()
                .anyMatch(p -> p.getDeviceSerial().equals(deviceSerial));
    }
}
