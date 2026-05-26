package com.realtimevideo.service;

import com.realtimevideo.model.Channel;
import com.realtimevideo.model.Device;
import com.realtimevideo.repository.ChannelRepository;
import com.realtimevideo.repository.DeviceRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 萤石云开放平台集成服务
 *
 * 使用萤石云 OpenAPI 获取 accessToken、设备和通道信息。
 * 文档: https://open.ys7.com/doc/zh/book/index.html
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EzvizService {

    private static final String TOKEN_URL = "https://open.ys7.com/api/lapp/token/get";
    private static final String DEVICE_LIST_URL = "https://open.ys7.com/api/lapp/device/list";
    private static final String CAMERA_LIST_URL = "https://open.ys7.com/api/lapp/device/camera/list";
    private static final String PTZ_START_URL = "https://open.ys7.com/api/lapp/device/ptz/start/%s/%d";
    private static final String PTZ_STOP_URL = "https://open.ys7.com/api/lapp/device/ptz/stop/%s/%d";

    @Value("${ezviz.app-key:}")
    private String appKey;

    @Value("${ezviz.app-secret:}")
    private String appSecret;

    private final DeviceRepository deviceRepository;
    private final ChannelRepository channelRepository;
    private final RestTemplate restTemplate;

    private final ReentrantLock tokenLock = new ReentrantLock();

    private String accessToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    // ---- 通道缓存（5秒TTL，减少数据库查询） ----
    private List<Channel> cachedChannels;
    private Instant channelsCacheExpiresAt = Instant.EPOCH;
    private final ReentrantLock channelsCacheLock = new ReentrantLock();

    // ================================================================
    //  Token 管理
    // ================================================================

    public String getAccessToken() {
        if (accessToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return accessToken;
        }
        return refreshAccessToken();
    }

    private String refreshAccessToken() {
        if (accessToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return accessToken;
        }
        tokenLock.lock();
        try {
            if (accessToken != null && Instant.now().isBefore(tokenExpiresAt)) {
                return accessToken;
            }
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("appKey", appKey);
            body.add("appSecret", appSecret);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    TOKEN_URL, new HttpEntity<>(body, headers), Map.class);

            if (response != null && "200".equals(String.valueOf(response.get("code")))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                this.accessToken = (String) data.get("accessToken");
                long expireTime = ((Number) data.get("expireTime")).longValue();
                this.tokenExpiresAt = Instant.ofEpochSecond(expireTime);
                log.info("萤石云 accessToken 获取成功，有效期至: {}", tokenExpiresAt);
                return this.accessToken;
            } else {
                String msg = response != null ? (String) response.get("msg") : "未知错误";
                throw new RuntimeException("获取萤石云Token失败: " + msg);
            }
        } catch (Exception e) {
            log.error("获取萤石云 accessToken 异常", e);
            throw new RuntimeException("获取萤石云 accessToken 失败: " + e.getMessage());
        } finally {
            tokenLock.unlock();
        }
    }

    public Map<String, String> getEzvizToken() {
        return Map.of(
                "accessToken", getAccessToken(),
                "appKey", appKey,
                "expiresIn", String.valueOf(Duration.between(Instant.now(), tokenExpiresAt).getSeconds())
        );
    }

    // ================================================================
    //  设备 & 通道同步 + 缓存
    // ================================================================

    /**
     * 获取所有通道（带 5 秒缓存）
     * 前端每 30 秒轮询一次，缓存可以显著减少数据库查询
     */
    public List<Channel> getAllChannels() {
        // 快速路径：缓存有效
        if (cachedChannels != null && Instant.now().isBefore(channelsCacheExpiresAt)) {
            return cachedChannels;
        }
        // 需要刷新
        channelsCacheLock.lock();
        try {
            if (cachedChannels != null && Instant.now().isBefore(channelsCacheExpiresAt)) {
                return cachedChannels;
            }
            this.cachedChannels = channelRepository.findAll();
            this.channelsCacheExpiresAt = Instant.now().plusSeconds(5);
            return this.cachedChannels;
        } finally {
            channelsCacheLock.unlock();
        }
    }

    public List<Channel> getDeviceChannels(String deviceSerial) {
        return channelRepository.findByDeviceSerialOrderByChannelNo(deviceSerial);
    }

    /**
     * 使通道缓存失效（syncDevices / enableAllPtz 后调用）
     */
    private void invalidateChannelsCache() {
        channelsCacheLock.lock();
        try {
            this.cachedChannels = null;
            this.channelsCacheExpiresAt = Instant.EPOCH;
        } finally {
            channelsCacheLock.unlock();
        }
    }

    /**
     * 从萤石云平台同步设备 + 通道列表到本地数据库
     *
     * 注意：不使用 @Transactional，每个设备的同步独立处理，
     * 一个设备失败不影响其他设备。
     */
    public List<Device> syncDevices() {
        String token = getAccessToken();
        log.info("开始从萤石云同步设备和通道...");

        List<Device> syncedDevices = new ArrayList<>();
        int pageStart = 0;
        int pageSize = 50;
        int total = Integer.MAX_VALUE;

        try {
            while (pageStart < total) {
                MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
                body.add("accessToken", token);
                body.add("pageStart", String.valueOf(pageStart));
                body.add("pageSize", String.valueOf(pageSize));

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.postForObject(
                        DEVICE_LIST_URL, new HttpEntity<>(body, headers), Map.class);

                if (response == null || !"200".equals(String.valueOf(response.get("code")))) {
                    String msg = response != null ? (String) response.get("msg") : "无响应";
                    throw new RuntimeException("获取设备列表失败: " + msg);
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> page = (Map<String, Object>) response.get("page");
                if (page != null) {
                    total = ((Number) page.get("total")).intValue();
                }

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> deviceList = (List<Map<String, Object>>) response.get("data");

                if (deviceList != null) {
                    for (Map<String, Object> ezDevice : deviceList) {
                        try {
                            Device localDevice = convertAndSaveDevice(ezDevice);
                            syncDeviceChannels(token, localDevice.getDeviceSerial());
                            syncedDevices.add(localDevice);
                        } catch (Exception e) {
                            String serial = (String) ezDevice.getOrDefault("deviceSerial", "unknown");
                            log.error("同步设备 {} 失败，跳过: {}", serial, e.getMessage());
                        }
                    }
                }
                pageStart += pageSize;
            }

            log.info("萤石云同步完成，共 {} 台设备", syncedDevices.size());
            invalidateChannelsCache();
            return syncedDevices;
        } catch (Exception e) {
            log.error("同步萤石云设备失败", e);
            throw new RuntimeException("同步萤石云设备失败: " + e.getMessage());
        }
    }

    private void syncDeviceChannels(String token, String deviceSerial) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("accessToken", token);
        body.add("deviceSerial", deviceSerial);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    CAMERA_LIST_URL, new HttpEntity<>(body, headers), Map.class);

            if (response == null || !"200".equals(String.valueOf(response.get("code")))) {
                log.warn("获取设备 {} 通道列表失败: {}", deviceSerial,
                        response != null ? response.get("msg") : "无响应");
                return;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cameraList = (List<Map<String, Object>>) response.get("data");
            if (cameraList == null || cameraList.isEmpty()) {
                log.debug("设备 {} 无通道或为 IPC 单通道设备", deviceSerial);
                return;
            }

            channelRepository.deleteByDeviceSerial(deviceSerial);

            String deviceName = deviceRepository.findByDeviceSerial(deviceSerial)
                    .map(Device::getDeviceName).orElse(deviceSerial);

            // 批量构建所有 Channel 实体
            List<Channel> channels = new ArrayList<>();
            for (Map<String, Object> cam : cameraList) {
                Integer channelNo = toIntOrNull(cam.get("channelNo"));
                if (channelNo == null) continue;

                int statusCode = ((Number) cam.getOrDefault("status", 0)).intValue();
                String status = switch (statusCode) {
                    case 1 -> "online";
                    case 2 -> "inactive";
                    case 3 -> "sleep";
                    default -> "offline";
                };

                channels.add(Channel.builder()
                        .deviceSerial(deviceSerial)
                        .deviceName(deviceName)
                        .channelNo(channelNo)
                        .channelName((String) cam.get("channelName"))
                        .status(status)
                        .ptzSupported("1".equals(String.valueOf(cam.getOrDefault("isSupportPTZ", "0"))))
                        .talkSupported("1".equals(String.valueOf(cam.getOrDefault("isSupportTalk", "0"))))
                        .playbackSupported("1".equals(String.valueOf(cam.getOrDefault("isSupportReplay", "0"))))
                        .picUrl((String) cam.get("picUrl"))
                        .build());
            }

            // 批量保存（一次 flush，代替逐条 save）
            channelRepository.saveAll(channels);
            log.debug("设备 {} 同步了 {} 个通道", deviceSerial, channels.size());
        } catch (Exception e) {
            log.error("同步设备 {} 通道失败: {}", deviceSerial, e.getMessage());
        }
    }

    private Device convertAndSaveDevice(Map<String, Object> ezDevice) {
        String deviceSerial = (String) ezDevice.get("deviceSerial");
        int statusCode = ((Number) ezDevice.getOrDefault("status", 0)).intValue();
        String status = switch (statusCode) {
            case 1 -> "online";
            case 2 -> "inactive";
            case 3 -> "sleep";
            default -> "offline";
        };

        Device device = deviceRepository.findByDeviceSerial(deviceSerial)
                .orElse(Device.builder()
                        .deviceSerial(deviceSerial)
                        .appKey(this.appKey)
                        .build());

        device.setDeviceName((String) ezDevice.get("deviceName"));
        device.setDeviceType((String) ezDevice.get("deviceType"));
        device.setStatus(status);
        return deviceRepository.save(device);
    }

    // ================================================================
    //  PTZ 控制
    // ================================================================

    public static final int DIR_LEFT = 1;
    public static final int DIR_RIGHT = 2;
    public static final int DIR_UP = 3;
    public static final int DIR_DOWN = 4;
    public static final int DIR_ZOOM_IN = 5;
    public static final int DIR_ZOOM_OUT = 6;

    public void startPtz(String deviceSerial, int channelNo, int direction, int speed) {
        String token = getAccessToken();
        String url = String.format(PTZ_START_URL, deviceSerial, channelNo);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("accessToken", token);
        body.add("direction", String.valueOf(direction));
        body.add("speed", String.valueOf(speed));
        executePtzRequest(url, body, "start", deviceSerial, channelNo);
    }

    public void stopPtz(String deviceSerial, int channelNo) {
        String token = getAccessToken();
        String url = String.format(PTZ_STOP_URL, deviceSerial, channelNo);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("accessToken", token);
        executePtzRequest(url, body, "stop", deviceSerial, channelNo);
    }

    private void executePtzRequest(String url, MultiValueMap<String, String> body,
                                    String action, String deviceSerial, int channelNo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    url, new HttpEntity<>(body, headers), Map.class);

            if (response == null || !"200".equals(String.valueOf(response.get("code")))) {
                String msg = response != null ? (String) response.get("msg") : "无响应";
                log.warn("PTZ {} 失败 [{}/CH{}]: {}", action, deviceSerial, channelNo, msg);
                if ("start".equals(action)) {
                    throw new RuntimeException("PTZ控制失败: " + msg);
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("PTZ {} 异常 [{}/CH{}]: {}", action, deviceSerial, channelNo, e.getMessage());
            throw new RuntimeException("PTZ控制失败: " + e.getMessage());
        }
    }

    // ================================================================
    //  批量操作
    // ================================================================

    public int enableAllPtz() {
        List<Channel> allChannels = channelRepository.findAll();
        int count = 0;
        for (Channel ch : allChannels) {
            if (!ch.getPtzSupported()) {
                ch.setPtzSupported(true);
                count++;
            }
        }
        if (count > 0) {
            channelRepository.saveAll(allChannels);
            invalidateChannelsCache();
        }
        log.info("已启用 {} 个通道的 PTZ 控制", count);
        return count;
    }

    // ================================================================
    //  初始化（异步，不阻塞启动）
    // ================================================================

    /**
     * 异步初始化萤石云服务：获取 Token + 同步设备
     * 使用 @Async 确保不阻塞 Spring Boot 启动
     */
    @Async
    @PostConstruct
    public void init() {
        if (appKey.isEmpty() || appSecret.isEmpty()) {
            log.warn("萤石云未配置 appKey/appSecret，跳过初始化");
            return;
        }
        try {
            getAccessToken();
            log.info("萤石云服务初始化成功");
            syncDevices();
        } catch (Exception e) {
            log.warn("萤石云服务初始化失败（将在首次 API 调用时自动重试）: {}", e.getMessage());
        }
    }

    // ================================================================
    //  工具方法
    // ================================================================

    private static Integer toIntOrNull(Object obj) {
        if (obj instanceof Number) return ((Number) obj).intValue();
        if (obj instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
