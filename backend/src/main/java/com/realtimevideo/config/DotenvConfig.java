package com.realtimevideo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 自动加载 .env 文件到 Spring 环境变量
 *
 * 在 application.yml 加载之前将 .env 内容注入，
 * 使 ${EZS_APP_KEY} 等占位符能正确替换。
 *
 * 优先级：系统环境变量 > .env 文件 > application.yml 默认值
 */
@Slf4j
public class DotenvConfig implements EnvironmentPostProcessor {

    private static final String DOTENV_FILE = ".env";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                        SpringApplication application) {
        Path dotenvPath = Paths.get(DOTENV_FILE);

        if (!Files.exists(dotenvPath)) {
            log.debug(".env 文件不存在: {}", dotenvPath.toAbsolutePath());
            return;
        }

        try {
            Map<String, Object> dotenv = new HashMap<>();
            int loaded = 0;

            for (String line : Files.readAllLines(dotenvPath)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                int eq = line.indexOf('=');
                if (eq < 1) continue;

                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();

                // 不覆盖已有的系统环境变量
                if (System.getenv().containsKey(key)) continue;

                dotenv.put(key, value);
                loaded++;
            }

            if (loaded > 0) {
                // 以最低优先级注入（系统环境变量 > JVM参数 > .env）
                environment.getPropertySources()
                        .addLast(new MapPropertySource("dotenv", dotenv));
                log.info("已从 .env 加载 {} 个配置项 (路径: {})", loaded, dotenvPath.toAbsolutePath());
            }
        } catch (IOException e) {
            log.warn("读取 .env 失败: {}", e.getMessage());
        }
    }
}
