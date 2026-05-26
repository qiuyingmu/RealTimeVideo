package com.realtimevideo.controller;

import com.realtimevideo.dto.ApiResponse;
import com.realtimevideo.model.Channel;
import com.realtimevideo.model.Device;
import com.realtimevideo.service.EzvizService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ezviz")
@RequiredArgsConstructor
public class EzvizController {

    private final EzvizService ezvizService;

    /**
     * 获取萤石云 accessToken（前端播放视频时需要）
     */
    @GetMapping("/token")
    public ResponseEntity<ApiResponse<Map<String, String>>> getToken() {
        Map<String, String> token = ezvizService.getEzvizToken();
        return ResponseEntity.ok(ApiResponse.success(token));
    }

    /**
     * 从萤石云平台同步设备 + 通道列表到本地数据库
     */
    @PostMapping("/sync-devices")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<Device>>> syncDevices() {
        List<Device> devices = ezvizService.syncDevices();
        return ResponseEntity.ok(ApiResponse.success(
                "同步成功，共 " + devices.size() + " 台设备", devices));
    }

    /**
     * 获取所有通道（摄像头）
     */
    @GetMapping("/channels")
    public ResponseEntity<ApiResponse<List<Channel>>> getAllChannels() {
        List<Channel> channels = ezvizService.getAllChannels();
        return ResponseEntity.ok(ApiResponse.success(channels));
    }

    /**
     * 获取指定设备的所有通道
     */
    @GetMapping("/devices/{deviceSerial}/channels")
    public ResponseEntity<ApiResponse<List<Channel>>> getDeviceChannels(
            @PathVariable String deviceSerial) {
        List<Channel> channels = ezvizService.getDeviceChannels(deviceSerial);
        return ResponseEntity.ok(ApiResponse.success(channels));
    }

    /**
     * 启用所有通道的 PTZ 云台控制功能
     * 调用后所有通道都可以使用云台控制（上下左右 + 变焦）
     */
    @PutMapping("/channels/ptz/enable-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Integer>> enableAllPtz() {
        int count = ezvizService.enableAllPtz();
        return ResponseEntity.ok(ApiResponse.success(
                "已启用 " + count + " 个通道的 PTZ 控制", count));
    }

    /**
     * PTZ 云台控制 - 开始转动
     *
     * @param request 包含 deviceSerial, channelNo, direction, speed(可选)
     */
    @PostMapping("/ptz/start")
    public ResponseEntity<ApiResponse<String>> startPtz(@RequestBody Map<String, Object> request) {
        String deviceSerial = (String) request.get("deviceSerial");
        int channelNo = ((Number) request.get("channelNo")).intValue();
        int direction = ((Number) request.get("direction")).intValue();
        int speed = request.containsKey("speed") ? ((Number) request.get("speed")).intValue() : 50;

        ezvizService.startPtz(deviceSerial, channelNo, direction, speed);
        return ResponseEntity.ok(ApiResponse.success("PTZ 控制已发送", "ok"));
    }

    /**
     * PTZ 云台控制 - 停止转动
     *
     * @param request 包含 deviceSerial, channelNo
     */
    @PostMapping("/ptz/stop")
    public ResponseEntity<ApiResponse<String>> stopPtz(@RequestBody Map<String, Object> request) {
        String deviceSerial = (String) request.get("deviceSerial");
        int channelNo = ((Number) request.get("channelNo")).intValue();

        ezvizService.stopPtz(deviceSerial, channelNo);
        return ResponseEntity.ok(ApiResponse.success("PTZ 停止已发送", "ok"));
    }
}
