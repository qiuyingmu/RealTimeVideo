package com.realtimevideo.service;

import com.realtimevideo.model.Device;
import com.realtimevideo.repository.DeviceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;

    /**
     * 获取所有设备
     */
    @Transactional(readOnly = true)
    public List<Device> getAllDevices() {
        return deviceRepository.findAll();
    }

    /**
     * 根据ID获取设备
     */
    @Transactional(readOnly = true)
    public Device getDeviceById(Long id) {
        return deviceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("设备不存在"));
    }

    /**
     * 根据设备序列号获取设备
     */
    @Transactional(readOnly = true)
    public Device getDeviceBySerial(String serial) {
        return deviceRepository.findByDeviceSerial(serial)
                .orElseThrow(() -> new EntityNotFoundException("设备不存在"));
    }

    /**
     * 添加设备
     */
    @Transactional
    public Device addDevice(Device device) {
        if (deviceRepository.existsByDeviceSerial(device.getDeviceSerial())) {
            throw new IllegalArgumentException("设备序列号已存在");
        }
        Device saved = deviceRepository.save(device);
        log.info("添加了新设备: {}", saved.getDeviceSerial());
        return saved;
    }

    /**
     * 更新设备
     */
    @Transactional
    public Device updateDevice(Long id, Device device) {
        Device existing = getDeviceById(id);
        existing.setDeviceName(device.getDeviceName());
        existing.setValidateCode(device.getValidateCode());
        existing.setStatus(device.getStatus());
        existing.setDeviceType(device.getDeviceType());
        existing.setAppKey(device.getAppKey());
        existing.setRemark(device.getRemark());
        Device saved = deviceRepository.save(existing);
        log.info("更新了设备: {}", saved.getDeviceSerial());
        return saved;
    }

    /**
     * 删除设备
     */
    @Transactional
    public void deleteDevice(Long id) {
        Device device = getDeviceById(id);
        deviceRepository.delete(device);
        log.info("已删除设备: {}", device.getDeviceSerial());
    }
}
