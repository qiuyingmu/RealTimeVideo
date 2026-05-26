package com.realtimevideo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启用异步任务和定时调度
 *
 * - @EnableAsync: EzvizService.init() 异步同步，不阻塞应用启动
 * - @EnableScheduling: 定时清理黑名单 / 限流器过期条目
 */
@Configuration
@EnableAsync
@EnableScheduling
public class SchedulingConfig {
}
