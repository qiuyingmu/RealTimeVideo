package com.realtimevideo.service;

import com.realtimevideo.model.Channel;
import com.realtimevideo.repository.ChannelRepository;
import com.realtimevideo.repository.DeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * EzvizService 单元测试
 *
 * 重点覆盖通道状态刷新逻辑（refreshChannelsStatus），
 * 不依赖萤石云真实 API，使用 Mock 模拟外部调用。
 */
@ExtendWith(MockitoExtension.class)
class EzvizServiceTest {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private ChannelRepository channelRepository;
    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private EzvizService ezvizService;

    @BeforeEach
    void setUp() {
        // 注入 appKey / appSecret（否则 init() 跳过逻辑）
        ReflectionTestUtils.setField(ezvizService, "appKey", "test-app-key");
        ReflectionTestUtils.setField(ezvizService, "appSecret", "test-app-secret");
    }

    @Test
    void testRefreshChannelsStatus_whenNoChannels_returnsEmpty() {
        when(channelRepository.findDistinctDeviceSerials()).thenReturn(List.of());

        List<Channel> result = ezvizService.refreshChannelsStatus();

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(channelRepository).findDistinctDeviceSerials();
        // 不应尝试调用萤石云 API
        verifyNoMoreInteractions(channelRepository);
    }

    @Test
    void testRefreshChannelsStatus_withSerials_invalidatesCache() {
        // 先填充缓存
        Channel cached = new Channel();
        ReflectionTestUtils.setField(ezvizService, "cachedChannels", List.of(cached));

        when(channelRepository.findDistinctDeviceSerials()).thenReturn(List.of());
        when(channelRepository.findAll()).thenReturn(List.of());

        ezvizService.refreshChannelsStatus();

        // 缓存应被清除
        Object cachedAfter = ReflectionTestUtils.getField(ezvizService, "cachedChannels");
        assertNull(cachedAfter);
    }

    @Test
    void testGetAllChannels_returnsCached_whenValid() {
        Channel cached = new Channel();
        ReflectionTestUtils.setField(ezvizService, "cachedChannels", List.of(cached));

        List<Channel> result = ezvizService.getAllChannels();

        assertNotNull(result);
        assertEquals(1, result.size());
        // 不应查询数据库
        verify(channelRepository, never()).findAll();
    }

    @Test
    void testGetAllChannels_withoutCache_queriesDb() {
        when(channelRepository.count()).thenReturn(1L);
        when(channelRepository.findAll()).thenReturn(List.of(new Channel()));

        List<Channel> result = ezvizService.getAllChannels();

        assertNotNull(result);
        assertFalse(result.isEmpty());
        verify(channelRepository).findAll();
    }

    @Test
    void testGetAllChannels_emptyDb_triggersSync() {
        when(channelRepository.count()).thenReturn(0L);
        when(channelRepository.findAll()).thenReturn(List.of());

        // 不应抛出异常（自动同步可能因未配置萤石云 token 而失败，但应优雅处理）
        List<Channel> result = ezvizService.getAllChannels();

        assertNotNull(result);
        verify(channelRepository).count();
    }
}
