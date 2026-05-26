package com.realtimevideo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateUserRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度需在3-50个字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100个字符")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 100, message = "密码长度需在8-100个字符之间")
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).{8,}$",
            message = "密码必须包含字母和数字，至少8位")
    private String password;

    @Size(max = 100, message = "显示名称长度不能超过100个字符")
    private String displayName;

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "^(ROLE_ADMIN|ROLE_USER)$", message = "角色必须是 ROLE_ADMIN 或 ROLE_USER")
    private String role;
}
