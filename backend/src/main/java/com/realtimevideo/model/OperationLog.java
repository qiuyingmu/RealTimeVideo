package com.realtimevideo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 操作审计日志
 *
 * 记录用户在系统中的关键操作，用于审计和追溯。
 */
@Entity
@Table(name = "operation_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 操作类型 */
    @Column(nullable = false, length = 50)
    private String action;

    /** 操作用户 ID */
    private Long userId;

    /** 操作用户名 */
    @Column(length = 50)
    private String username;

    /** 操作目标（如设备序列号、通道号） */
    @Column(length = 200)
    private String target;

    /** 操作详情 */
    @Column(length = 1000)
    private String details;

    /** 请求来源 IP */
    @Column(length = 50)
    private String ipAddress;

    /** 记录时间 */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
