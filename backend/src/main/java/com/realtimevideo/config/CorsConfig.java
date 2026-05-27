package com.realtimevideo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    @Value("${cors.allowed-origin-patterns:}")
    private String allowedOriginPatterns;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // 将逗号分隔的 origins 字符串解析为列表
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        boolean hasWildcard = origins.contains("*");

        if (hasWildcard) {
            // 开发环境：允许所有来源但不带 credentials
            config.setAllowedOriginPatterns(List.of("*"));
            config.setAllowCredentials(false);
        } else {
            // 配置的特定来源（精确匹配）
            config.setAllowedOrigins(origins);

            // 如有配置通配符模式（如 https?://192.168.*:*），一并添加
            if (org.springframework.util.StringUtils.hasText(allowedOriginPatterns)) {
                List<String> patterns = Arrays.asList(allowedOriginPatterns.split(","));
                config.setAllowedOriginPatterns(patterns);
            }

            config.setAllowCredentials(true);
        }

        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
        config.setExposedHeaders(Arrays.asList("Authorization", "Retry-After"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);

        return new CorsFilter(source);
    }
}
