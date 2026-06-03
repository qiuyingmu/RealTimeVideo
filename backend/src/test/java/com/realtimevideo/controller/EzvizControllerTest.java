package com.realtimevideo.controller;

import com.realtimevideo.model.Channel;
import com.realtimevideo.service.AuditLogService;
import com.realtimevideo.service.EzvizService;
import com.realtimevideo.repository.UserDevicePermissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * EzvizController 单元测试
 *
 * 验证认证/权限检查以及新接口 /api/ezviz/channels/refresh-status 的访问控制。
 */
@WebMvcTest(EzvizController.class)
class EzvizControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EzvizService ezvizService;

    @MockBean
    private AuditLogService auditLogService;

    @MockBean
    private UserDevicePermissionRepository permissionRepository;

    @Test
    void testRefreshStatus_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/ezviz/channels/refresh-status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    void testRefreshStatus_withUserRole_returns200() throws Exception {
        // 普通用户有权限设备返回空列表
        when(ezvizService.refreshChannelsStatus()).thenReturn(List.of());
        when(permissionRepository.findByUsername("user")).thenReturn(List.of());

        mockMvc.perform(get("/api/ezviz/channels/refresh-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testRefreshStatus_withAdminRole_returns200() throws Exception {
        Channel ch = new Channel();
        when(ezvizService.refreshChannelsStatus()).thenReturn(List.of(ch));

        mockMvc.perform(get("/api/ezviz/channels/refresh-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
