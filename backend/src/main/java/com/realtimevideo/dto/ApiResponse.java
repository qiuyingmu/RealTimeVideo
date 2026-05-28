package com.realtimevideo.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;

    private static final ObjectMapper JSON = new ObjectMapper();

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message("操作成功")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .build();
    }

    public static <T> ApiResponse<T> error(String message, T data) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * 将错误响应写入 HttpServletResponse（用于 Filter 等非 Controller 场景）
     * 避免各处手动拼接 JSON 字符串
     */
    public static void writeError(HttpServletResponse response, int status, String message) {
        try {
            response.setStatus(status);
            response.setContentType("application/json;charset=UTF-8");
            JSON.writeValue(response.getWriter(), ApiResponse.error(message));
        } catch (IOException e) {
            // 写入响应失败，静默处理
        }
    }

    /**
     * 将错误响应（含扩展字段）写入 HttpServletResponse
     */
    public static void writeError(HttpServletResponse response, int status, String message, Object extraData) {
        try {
            response.setStatus(status);
            response.setContentType("application/json;charset=UTF-8");
            JSON.writeValue(response.getWriter(), ApiResponse.error(message, extraData));
        } catch (IOException e) {
            // 写入响应失败，静默处理
        }
    }
}
