package com.realtimevideo.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String accessToken;

    /** refreshToken 仅用于 httpOnly Cookie 下发，不序列化到 JSON */
    @JsonIgnore
    private String refreshToken;

    private String tokenType;
    private long expiresIn;
    private String username;
    private String displayName;
    private String role;

    /** 是否需要强制修改密码（首次登录） */
    private boolean passwordChangeRequired;
}
