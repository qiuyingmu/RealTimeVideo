package com.realtimevideo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户设备权限映射
 *
 * 记录每个用户可以访问哪些设备。
 * deviceSerial 授权表示该用户可查看该设备的所有通道。
 */
@Entity
@Table(name = "user_device_permissions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "device_serial"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDevicePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 用户名（冗余，方便查询展示） */
    @Column(length = 50)
    private String username;

    /** 被授权的设备序列号 */
    @Column(name = "device_serial", nullable = false, length = 100)
    private String deviceSerial;

    /** 设备名称（冗余，方便展示） */
    @Column(name = "device_name", length = 200)
    private String deviceName;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
