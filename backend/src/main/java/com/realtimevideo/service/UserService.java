package com.realtimevideo.service;

import com.realtimevideo.dto.CreateUserRequest;
import com.realtimevideo.dto.LoginRequest;
import com.realtimevideo.dto.LoginResponse;
import com.realtimevideo.model.Role;
import com.realtimevideo.model.User;
import com.realtimevideo.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /** 密码最小长度 */
    private static final int PASSWORD_MIN_LENGTH = 6;
    /** 密码最大长度 */
    private static final int PASSWORD_MAX_LENGTH = 18;

    @Value("${security.login.max-attempts:5}")
    private int maxLoginAttempts;

    @Value("${security.login.lock-duration-minutes:30}")
    private int lockDurationMinutes;

    /**
     * 用户登录
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    log.warn("登录失败：用户 {} 不存在", request.getUsername());
                    return new BadCredentialsException("用户名或密码错误");
                });

        // 检查账户是否被锁定
        if (!user.isAccountNonLocked()) {
            long remainingMinutes = java.time.Duration.between(LocalDateTime.now(), user.getLockTime()).toMinutes();
            log.warn("登录失败：用户 {} 账户已锁定，剩余 {} 分钟", request.getUsername(), remainingMinutes);
            throw new LockedException("账户已被锁定，请在 " + remainingMinutes + " 分钟后重试");
        }

        // 检查账户是否启用
        if (!user.isEnabled()) {
            log.warn("登录失败：用户 {} 账户已被禁用", request.getUsername());
            throw new DisabledException("账户已被禁用，请联系管理员");
        }

        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            handleFailedLogin(user);
            throw new BadCredentialsException("用户名或密码错误");
        }

        // 登录成功，重置失败次数
        resetLoginAttempts(user);

        // 更新最后登录时间
        userRepository.updateLastLogin(user.getUsername(), LocalDateTime.now());

        // 生成Token
        String accessToken = jwtService.generateAccessToken(user.getUsername(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user.getUsername());

        log.info("用户 {} 登录成功", user.getUsername());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiration())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .role(user.getRole().name())
                .passwordChangeRequired(user.isPasswordChangeRequired())
                .build();
    }

    /**
     * 刷新Token
     */
    @Transactional
    public LoginResponse refreshToken(String refreshToken) {
        // 验证Refresh Token
        if (!jwtService.validateToken(refreshToken)) {
            throw new BadCredentialsException("刷新令牌无效或已过期");
        }

        // 确认是Refresh Token
        String tokenType = jwtService.extractTokenType(refreshToken);
        if (!"refresh".equals(tokenType)) {
            throw new BadCredentialsException("无效的令牌类型");
        }

        String username = jwtService.extractUsername(refreshToken);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));

        if (!user.isEnabled()) {
            throw new DisabledException("账户已被禁用");
        }

        // 生成新的Token对
        String newAccessToken = jwtService.generateAccessToken(username, user.getRole().name());
        String newRefreshToken = jwtService.generateRefreshToken(username);

        // 将旧的Refresh Token加入黑名单
        jwtService.blacklistToken(refreshToken);

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiration())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .role(user.getRole().name())
                .passwordChangeRequired(user.isPasswordChangeRequired())
                .build();
    }

    /**
     * 用户修改密码（首次登录强制修改 / 自助修改）
     *
     * @param username     当前登录用户名（从 SecurityContext 获取）
     * @param oldPassword  旧密码
     * @param newPassword  新密码（6~18 位，可纯数字）
     */
    @Transactional
    public LoginResponse changePassword(String username, String oldPassword, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));

        // 验证旧密码
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            log.warn("修改密码失败：用户 {} 旧密码错误", username);
            throw new BadCredentialsException("当前密码错误");
        }

        // 验证新密码长度（6~18 位）
        if (newPassword == null || newPassword.length() < PASSWORD_MIN_LENGTH || newPassword.length() > PASSWORD_MAX_LENGTH) {
            throw new IllegalArgumentException("密码长度必须为 " + PASSWORD_MIN_LENGTH + "~" + PASSWORD_MAX_LENGTH + " 位");
        }

        // 旧密码和新密码不能相同
        if (oldPassword.equals(newPassword)) {
            throw new IllegalArgumentException("新密码不能与当前密码相同");
        }

        String encodedNewPassword = passwordEncoder.encode(newPassword);
        userRepository.updatePassword(username, encodedNewPassword);

        // 重置锁定状态
        user.setAccountNonLocked(true);
        user.setLockTime(null);
        user.setFailedLoginAttempts(0);

        log.info("用户 {} 密码修改成功", username);

        // 生成新 Token（密码变了，签发新 Token 使旧 token 失效更安全）
        String newAccessToken = jwtService.generateAccessToken(username, user.getRole().name());
        String newRefreshToken = jwtService.generateRefreshToken(username);

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiration())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .role(user.getRole().name())
                .passwordChangeRequired(false)
                .build();
    }

    /**
     * 用户登出
     */
    @Transactional
    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null) {
            jwtService.blacklistToken(accessToken);
        }
        if (refreshToken != null) {
            jwtService.blacklistToken(refreshToken);
        }
        log.info("用户登出成功");
    }

    /**
     * 管理员创建新用户
     */
    @Transactional
    public User createUser(CreateUserRequest request) {
        // 检查用户名是否已存在
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("用户名已存在");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName())
                .role(Role.valueOf(request.getRole()))
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .failedLoginAttempts(0)
                .build();

        User saved = userRepository.save(user);
        log.info("管理员创建了新用户: {}", saved.getUsername());
        return saved;
    }

    /**
     * 获取所有用户
     */
    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * 根据ID获取用户
     */
    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在"));
    }

    /**
     * 启用/禁用用户
     */
    @Transactional
    public User toggleUserEnabled(Long id) {
        User user = getUserById(id);
        user.setEnabled(!user.isEnabled());
        // 如果启用，重置锁定状态
        if (user.isEnabled()) {
            user.setAccountNonLocked(true);
            user.setLockTime(null);
            user.setFailedLoginAttempts(0);
        }
        User saved = userRepository.save(user);
        log.info("用户 {} 状态已变更为: enabled={}", saved.getUsername(), saved.isEnabled());
        return saved;
    }

    /**
     * 重置用户密码（管理员）
     */
    @Transactional
    public User resetPassword(Long id, String newPassword) {
        User user = getUserById(id);
        user.setPassword(passwordEncoder.encode(newPassword));
        // 重置锁定状态
        user.setAccountNonLocked(true);
        user.setLockTime(null);
        user.setFailedLoginAttempts(0);
        User saved = userRepository.save(user);
        log.info("管理员重置了用户 {} 的密码", saved.getUsername());
        return saved;
    }

    /**
     * 删除用户
     */
    @Transactional
    public void deleteUser(Long id) {
        User user = getUserById(id);
        userRepository.delete(user);
        log.info("用户 {} 已被删除", user.getUsername());
    }

    // ========== Private Methods ==========

    private void handleFailedLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= maxLoginAttempts) {
            LocalDateTime lockTime = LocalDateTime.now().plusMinutes(lockDurationMinutes);
            user.setAccountNonLocked(false);
            user.setLockTime(lockTime);
            userRepository.lockAccount(user.getUsername(), attempts, lockTime);
            log.warn("用户 {} 登录失败 {} 次，账户已被锁定至 {}",
                    user.getUsername(), attempts, lockTime);
        } else {
            userRepository.incrementFailedAttempts(user.getUsername());
            log.warn("用户 {} 登录失败第 {} 次", user.getUsername(), attempts);
        }
    }

    private void resetLoginAttempts(User user) {
        if (user.getFailedLoginAttempts() > 0) {
            userRepository.resetFailedAttempts(user.getUsername());
        }
    }
}
