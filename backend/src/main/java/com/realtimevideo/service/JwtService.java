package com.realtimevideo.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration:900000}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration:604800000}")
    private long refreshTokenExpiration;

    /** 黑名单：存储已登出的 token → 过期时间 */
    private final ConcurrentHashMap<String, Date> blacklistedTokens = new ConcurrentHashMap<>();

    private volatile SecretKey signingKey;

    private SecretKey getSigningKey() {
        if (signingKey != null) return signingKey;
        synchronized (this) {
            if (signingKey != null) return signingKey;
            try {
                byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
                this.signingKey = Keys.hmacShaKeyFor(keyBytes);
                log.info("JWT 密钥加载成功 ({} bits)", keyBytes.length * 8);
            } catch (WeakKeyException | IllegalArgumentException e) {
                log.warn("JWT 密钥强度不足 ({}), 自动生成临时密钥", e.getMessage());
                log.warn("⚠️  警告：自动生成的密钥在重启后会丢失！生产环境必须在 .env 或环境变量中配置 JWT_SECRET");
                log.warn("⚠️  生成命令: openssl rand -base64 32");
                this.signingKey = Jwts.SIG.HS256.key().build();
            }
            return signingKey;
        }
    }

    // ---- Token 生成 ----

    public String generateAccessToken(String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("type", "access");
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    // ---- Token 解析 ----

    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }

    public String extractTokenType(String token) {
        return extractClaims(token).get("type", String.class);
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = extractClaims(token);
            if (isTokenBlacklisted(token)) return false;
            return !claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // ---- 黑名单管理 ----

    public void blacklistToken(String token) {
        try {
            Claims claims = extractClaims(token);
            blacklistedTokens.put(token, claims.getExpiration());
        } catch (JwtException e) {
            // 无效 token 无需加入黑名单
        }
    }

    public boolean isTokenBlacklisted(String token) {
        Date expiration = blacklistedTokens.get(token);
        if (expiration == null) return false;
        if (expiration.before(new Date())) {
            blacklistedTokens.remove(token);
            return false;
        }
        return true;
    }

    /**
     * 定时清理已过期的黑名单条目（每 5 分钟）
     * 防止内存泄漏
     */
    @Scheduled(fixedRate = 300_000)
    public void cleanExpiredBlacklistedTokens() {
        Date now = new Date();
        int before = blacklistedTokens.size();
        blacklistedTokens.entrySet().removeIf(e -> e.getValue().before(now));
        int removed = before - blacklistedTokens.size();
        if (removed > 0) {
            log.debug("清理了 {} 个已过期的黑名单 Token，剩余 {}", removed, blacklistedTokens.size());
        }
    }

    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
