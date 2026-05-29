package com.realtimevideo.config;

import com.realtimevideo.model.Role;
import com.realtimevideo.model.User;
import com.realtimevideo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // 检查是否已存在admin用户
        if (!userRepository.existsByUsername("admin")) {
            User admin = User.builder()
                    .username("admin")
                    .email("admin@realtimevideo.com")
                    .password(passwordEncoder.encode("Admin@123456"))
                    .displayName("系统管理员")
                    .role(Role.ROLE_ADMIN)
                    .enabled(true)
                    .accountNonExpired(true)
                    .accountNonLocked(true)
                    .credentialsNonExpired(true)
                    .failedLoginAttempts(0)
                    .passwordChangeRequired(true)
                    .build();
            userRepository.save(admin);
            log.info("========================================");
            log.info("默认管理员账户已创建:");
            log.info("  用户名: admin");
            log.info("  密码: Admin@123456");
            log.info("  重要: 首次登录后请立即修改密码!");
            log.info("========================================");
        } else {
            log.info("管理员账户已存在，跳过初始化");
        }

        // 可选：创建一个示例普通用户
        if (!userRepository.existsByUsername("user")) {
            User normalUser = User.builder()
                    .username("user")
                    .email("user@realtimevideo.com")
                    .password(passwordEncoder.encode("User@123456"))
                    .displayName("普通用户")
                    .role(Role.ROLE_USER)
                    .enabled(true)
                    .accountNonExpired(true)
                    .accountNonLocked(true)
                    .credentialsNonExpired(true)
                    .failedLoginAttempts(0)
                    .passwordChangeRequired(true)
                    .build();
            userRepository.save(normalUser);
            log.info("示例普通用户已创建: user / User@123456");
        }
    }
}
