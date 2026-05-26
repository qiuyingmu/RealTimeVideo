package com.realtimevideo.service;

import com.realtimevideo.model.OperationLog;
import com.realtimevideo.repository.OperationLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 操作审计日志服务
 *
 * 记录用户关键操作行为，支持分页查询和筛选。
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final OperationLogRepository operationLogRepository;

    /**
     * 记录操作日志（自动从 SecurityContext 获取当前用户）
     */
    public void log(String action, String target, String details, HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = null;
        String username = null;

        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            Object principal = auth.getPrincipal();
            if (principal instanceof String) {
                username = (String) principal;
            } else if (principal instanceof org.springframework.security.core.userdetails.User) {
                username = ((org.springframework.security.core.userdetails.User) principal).getUsername();
            }
            // username might be stored in the principal name
            if (username == null) {
                username = auth.getName();
            }
        }

        String ip = null;
        if (request != null) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                ip = xForwardedFor.split(",")[0].trim();
            } else {
                ip = request.getRemoteAddr();
            }
        }

        save(userId, username, action, target, details, ip);
    }

    /**
     * 带 userId/username 的日志记录（用于登录等 SecurityContext 尚未设置的场景）
     */
    public void log(Long userId, String username, String action, String target, String details, String ip) {
        save(userId, username, action, target, details, ip);
    }

    private void save(Long userId, String username, String action, String target, String details, String ip) {
        OperationLog log = OperationLog.builder()
                .userId(userId)
                .username(username)
                .action(action)
                .target(target)
                .details(details)
                .ipAddress(ip)
                .build();
        operationLogRepository.save(log);
    }

    /**
     * 查询操作日志（分页）
     */
    @Transactional(readOnly = true)
    public Page<OperationLog> getLogs(Pageable pageable, String action, String username) {
        if (action != null && !action.isEmpty() && username != null && !username.isEmpty()) {
            return operationLogRepository.findByActionAndUsernameOrderByCreatedAtDesc(action, username, pageable);
        }
        if (action != null && !action.isEmpty()) {
            return operationLogRepository.findByActionOrderByCreatedAtDesc(action, pageable);
        }
        if (username != null && !username.isEmpty()) {
            return operationLogRepository.findByUsernameOrderByCreatedAtDesc(username, pageable);
        }
        return operationLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }
}
