package com.realtimevideo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * refreshToken 已迁移到 httpOnly Cookie 方式传输。
 * 此 DTO 保留作向后兼容，新代码应使用 {@code @CookieValue("refresh_token")}。
 */
@Deprecated
@Data
public class RefreshTokenRequest {

    @NotBlank(message = "刷新令牌不能为空")
    private String refreshToken;
}
