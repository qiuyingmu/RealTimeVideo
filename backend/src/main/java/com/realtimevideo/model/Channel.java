package com.realtimevideo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 萤石云设备通道（摄像头）
 *
 * 一个设备（如NVR录像机）可能包含多个通道，每个通道对应一个摄像头。
 * 播放视频时需要使用通道号（channelNo）。
 */
@Entity
@Table(name = "channels", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"device_serial", "channel_no"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属设备名称（冗余存储，避免频繁关联查询） */
    @Column(name = "device_name", length = 200)
    private String deviceName;

    /** 所属设备序列号 */
    @Column(name = "device_serial", nullable = false, length = 100)
    private String deviceSerial;

    /** 通道号（从1开始，如1,2,3,4） */
    @Column(name = "channel_no", nullable = false)
    private Integer channelNo;

    /** 通道名称（如"正门","侧门"） */
    @Column(length = 200)
    private String channelName;

    /** 通道状态: online/offline */
    @Column(length = 20)
    private String status;

    /** 是否支持云台控制 */
    @Builder.Default
    private Boolean ptzSupported = false;

    /** 是否支持对讲 */
    @Builder.Default
    private Boolean talkSupported = false;

    /** 是否支持回放 */
    @Builder.Default
    private Boolean playbackSupported = false;

    /** 缩略图URL */
    @Column(length = 500)
    private String picUrl;

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

    /**
     * 生成萤石云播放地址
     * 格式: ezopen://open.ys7.com/{deviceSerial}/{channelNo}.hd.live
     */
    public String getEzvizPlayUrl() {
        return String.format("ezopen://open.ys7.com/%s/%d.hd.live", deviceSerial, channelNo);
    }
}
