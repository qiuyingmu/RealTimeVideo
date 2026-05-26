package com.realtimevideo.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate 连接池配置
 *
 * 为萤石云 OpenAPI 调用提供连接池支持，
 * 减少频繁创建连接的开销，提高 API 调用性能。
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                // 连接超时：5秒（萤石云 API 通常响应很快）
                .setConnectTimeout(Duration.ofSeconds(5))
                // 读取超时：10秒（获取设备列表等操作）
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }
}
