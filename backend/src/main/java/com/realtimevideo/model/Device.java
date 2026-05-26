package com.realtimevideo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "devices")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 设备序列号 */
    @Column(nullable = false, length = 100, unique = true)
    private String deviceSerial;

    /** 设备名称 */
    @Column(length = 200)
    private String deviceName;

    /** 设备验证码 */
    @Column(length = 50)
    private String validateCode;

    /** 设备状态: online/offline */
    @Column(length = 20)
    private String status;

    /** 设备类型 */
    @Column(length = 100)
    private String deviceType;

    /** 所属萤石云APP Key */
    @Column(length = 100)
    private String appKey;

    /** 备注 */
    @Column(length = 500)
    private String remark;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
