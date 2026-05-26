package com.realtimevideo.controller;

import com.realtimevideo.dto.ApiResponse;
import com.realtimevideo.model.Device;
import com.realtimevideo.service.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    /**
     * 获取所有设备（所有认证用户可查看）
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Device>>> getAllDevices() {
        List<Device> devices = deviceService.getAllDevices();
        return ResponseEntity.ok(ApiResponse.success(devices));
    }

    /**
     * 根据ID获取设备
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Device>> getDeviceById(@PathVariable Long id) {
        Device device = deviceService.getDeviceById(id);
        return ResponseEntity.ok(ApiResponse.success(device));
    }

    /**
     * 根据序列号获取设备
     */
    @GetMapping("/serial/{serial}")
    public ResponseEntity<ApiResponse<Device>> getDeviceBySerial(@PathVariable String serial) {
        Device device = deviceService.getDeviceBySerial(serial);
        return ResponseEntity.ok(ApiResponse.success(device));
    }

    /**
     * 添加设备（管理员专用）
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Device>> addDevice(@RequestBody Device device) {
        Device saved = deviceService.addDevice(device);
        return ResponseEntity.ok(ApiResponse.success("设备添加成功", saved));
    }

    /**
     * 更新设备（管理员专用）
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Device>> updateDevice(
            @PathVariable Long id, @RequestBody Device device) {
        Device updated = deviceService.updateDevice(id, device);
        return ResponseEntity.ok(ApiResponse.success("设备更新成功", updated));
    }

    /**
     * 删除设备（管理员专用）
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteDevice(@PathVariable Long id) {
        deviceService.deleteDevice(id);
        return ResponseEntity.ok(ApiResponse.success("设备删除成功", null));
    }
}
