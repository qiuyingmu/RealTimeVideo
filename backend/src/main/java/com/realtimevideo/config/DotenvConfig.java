package com.realtimevideo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 自动加载 .env 文件中的环境变量
 *
 * 在 Spring Boot 加载 application.yml 之前读取 .env，
 * 使 ${EZS_APP_KEY} 等占位符能正确替换。
 *
 * 加载顺序（优先级从高到低）：
 * 1. 系统环境变量 / IDEA Run Configuration
 * 2. 当前工作目录下的 .env 文件
 * 3. application.yml 中的默认值
 */
@Slf4j
public class DotenvConfig implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment env = event.getEnvironment();
        Path dotenvPath = Paths.get(".env");

        if (!Files.exists(dotenvPath)) {
            log.debug(".env 文件不存在，跳过加载 (工作目录: {})", System.getProperty("user.dir"));
            return;
        }

        try {
            int loaded = 0;
            for (String line : Files.readAllLines(dotenvPath)) {
                line = line.trim();
                // 跳过空行和注释
                if (line.isEmpty() || line.startsWith("#")) continue;

                int eq = line.indexOf('=');
                if (eq < 1) continue;

                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();

                // 不覆盖已有的环境变量
                if (System.getenv(key) != null) continue;

                System.setProperty(key, value);
                loaded++;
            }
            if (loaded > 0) {
                log.info("已从 .env 文件加载 {} 个配置项", loaded);
            }
        } catch (IOException e) {
            log.warn("读取 .env 文件失败: {}", e.getMessage());
        }
    }
}
