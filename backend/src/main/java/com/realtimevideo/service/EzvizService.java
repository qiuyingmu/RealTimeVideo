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
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.locks.ReentrantLock;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 萤石云开放平台集成服务
 *
 * 使用萤石云 OpenAPI 获取 accessToken、设备和通道信息。
 * 文档: https://open.ys7.com/doc/zh/book/index.html
 *
 * OpenAPI 调用说明：
 * 萤石云 OpenAPI 使用 POST + form-data 格式（非 JSON body），
 * accessToken 放在请求体中传递（非 Header）。
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

    /** Token 刷新锁，防止并发刷新 */
    private final ReentrantLock tokenLock = new ReentrantLock();

    private String accessToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    /**
     * 获取萤石云 accessToken（自动缓存刷新）
     */
    public String getAccessToken() {
        if (accessToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return accessToken;
        }
        return refreshAccessToken();
    }

    private String refreshAccessToken() {
        // 先快速检查，避免锁竞争
        if (accessToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return accessToken;
        }
        tokenLock.lock();
        try {
            // 双重检查：获取锁后再次检查是否已被其他线程刷新
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
                    TOKEN_URL,
                    new HttpEntity<>(body, headers),
                    Map.class);

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

    /**
     * 提供给前端的 accessToken（用于 EZUIKit 播放器）
     */
    public Map<String, String> getEzvizToken() {
        return Map.of(
                "accessToken", getAccessToken(),
                "appKey", appKey,
                "expiresIn", String.valueOf(
                        Duration.between(Instant.now(), tokenExpiresAt).getSeconds())
        );
    }

    /**
     * 从萤石云平台同步设备 + 通道列表到本地数据库
     *
     * 1. 调用萤石云 OpenAPI 获取所有设备
     * 2. 对每个设备调用通道列表 API 获取通道（摄像头）
     * 3. 设备列表和通道列表写入本地数据库
     *
     * 注意：不使用 @Transactional，每个设备的同步独立处理，
     * 一个设备失败不影响其他设备。
     *
     * @return 同步后的完整设备列表（含通道信息通过 deviceSerial 关联）
     */
    public List<Device> syncDevices() {
        String token = getAccessToken();
        log.info("开始从萤石云同步设备和通道...");

        List<Device> syncedDevices = new ArrayList<>();
        int pageStart = 0;
        int pageSize = 50;
        int total = Integer.MAX_VALUE;

        try {
            // 第一步：获取所有设备
            while (pageStart < total) {
                MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
                body.add("accessToken", token);
                body.add("pageStart", String.valueOf(pageStart));
                body.add("pageSize", String.valueOf(pageSize));

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.postForObject(
                        DEVICE_LIST_URL,
                        new HttpEntity<>(body, headers),
                        Map.class);

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
                List<Map<String, Object>> deviceList =
                        (List<Map<String, Object>>) response.get("data");

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
            return syncedDevices;

        } catch (Exception e) {
            log.error("同步萤石云设备失败", e);
            throw new RuntimeException("同步萤石云设备失败: " + e.getMessage());
        }
    }

    /**
     * 同步单个设备的所有通道
     * 调用萤石云摄像头列表 API
     */
    private void syncDeviceChannels(String token, String deviceSerial) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("accessToken", token);
        body.add("deviceSerial", deviceSerial);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    CAMERA_LIST_URL,
                    new HttpEntity<>(body, headers),
                    Map.class);

            if (response == null || !"200".equals(String.valueOf(response.get("code")))) {
                log.warn("获取设备 {} 通道列表失败: {}", deviceSerial,
                        response != null ? response.get("msg") : "无响应");
                return;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cameraList =
                    (List<Map<String, Object>>) response.get("data");

            if (cameraList == null || cameraList.isEmpty()) {
                log.debug("设备 {} 无通道或为 IPC 单通道设备", deviceSerial);
                return;
            }

            // 清除旧通道数据，重新写入
            channelRepository.deleteByDeviceSerial(deviceSerial);

            // 获取设备名称
            String deviceName = deviceRepository.findByDeviceSerial(deviceSerial)
                    .map(Device::getDeviceName)
                    .orElse(deviceSerial);

            for (Map<String, Object> cam : cameraList) {
                Integer channelNo = null;
                Object chObj = cam.get("channelNo");
                if (chObj instanceof Number) {
                    channelNo = ((Number) chObj).intValue();
                } else if (chObj instanceof String) {
                    channelNo = Integer.parseInt((String) chObj);
                }

                if (channelNo == null) continue;

                String channelName = (String) cam.get("channelName");

                // 状态转换
                int statusCode = ((Number) cam.getOrDefault("status", 0)).intValue();
                String status = switch (statusCode) {
                    case 1 -> "online";
                    case 2 -> "inactive";
                    case 3 -> "sleep";
                    default -> "offline";
                };

                // 是否支持云台控制
                boolean ptzSupported = "1".equals(String.valueOf(cam.getOrDefault("isSupportPTZ", "0")));
                boolean talkSupported = "1".equals(String.valueOf(cam.getOrDefault("isSupportTalk", "0")));
                boolean playbackSupported = "1".equals(String.valueOf(cam.getOrDefault("isSupportReplay", "0")));
                String picUrl = (String) cam.get("picUrl");

                Channel channel = Channel.builder()
                        .deviceSerial(deviceSerial)
                        .deviceName(deviceName)
                        .channelNo(channelNo)
                        .channelName(channelName)
                        .status(status)
                        .ptzSupported(ptzSupported)
                        .talkSupported(talkSupported)
                        .playbackSupported(playbackSupported)
                        .picUrl(picUrl)
                        .build();

                channelRepository.save(channel);
            }

            long count = channelRepository.countByDeviceSerial(deviceSerial);
            log.debug("设备 {} 同步了 {} 个通道", deviceSerial, count);

        } catch (Exception e) {
            log.error("同步设备 {} 通道失败: {}", deviceSerial, e.getMessage());
        }
    }

    /**
     * 获取某个设备的所有通道
     */
    public List<Channel> getDeviceChannels(String deviceSerial) {
        return channelRepository.findByDeviceSerialOrderByChannelNo(deviceSerial);
    }

    /**
     * 获取所有设备的所有通道（展平视图）
     */
    public List<Channel> getAllChannels() {
        return channelRepository.findAll();
    }

    /**
     * 将萤石云 API 返回的设备转换为本地 Device 实体并保存
     */
    private Device convertAndSaveDevice(Map<String, Object> ezDevice) {
        String deviceSerial = (String) ezDevice.get("deviceSerial");
        String deviceName = (String) ezDevice.get("deviceName");
        String deviceType = (String) ezDevice.get("deviceType");

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

        device.setDeviceName(deviceName);
        device.setDeviceType(deviceType);
        device.setStatus(status);

        return deviceRepository.save(device);
    }

    /**
     * 启用所有通道的 PTZ（云台）控制功能
     * 部分 NVR 摄像头虽然硬件支持 PTZ，但 API 返回的 isSupportPTZ 可能为 false，
     * 调用此方法可强制开启所有通道的 PTZ 控制。
     *
     * @return 更新的通道数量
     */
    public int enableAllPtz() {
        List<Channel> allChannels = channelRepository.findAll();
        int count = 0;
        for (Channel ch : allChannels) {
            if (!ch.getPtzSupported()) {
                ch.setPtzSupported(true);
                channelRepository.save(ch);
                count++;
            }
        }
        log.info("已启用 {} 个通道的 PTZ 控制", count);
        return count;
    }

    /**
     * 方向常量映射表
     */
    public static final int DIR_LEFT = 1;
    public static final int DIR_RIGHT = 2;
    public static final int DIR_UP = 3;
    public static final int DIR_DOWN = 4;
    public static final int DIR_ZOOM_IN = 5;
    public static final int DIR_ZOOM_OUT = 6;

    /**
     * 发送 PTZ 云台控制命令（开始转动）
     *
     * 调用萤石云 OpenAPI 控制摄像头云台转动。
     *
     * @param deviceSerial 设备序列号
     * @param channelNo    通道号
     * @param direction    方向 (1=左, 2=右, 3=上, 4=下, 5=变焦+, 6=变焦-)
     * @param speed        速度 (0-100, 默认50)
     */
    public void startPtz(String deviceSerial, int channelNo, int direction, int speed) {
        String token = getAccessToken();
        String url = String.format(PTZ_START_URL, deviceSerial, channelNo);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("accessToken", token);
        body.add("direction", String.valueOf(direction));
        body.add("speed", String.valueOf(speed));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    url, new HttpEntity<>(body, headers), Map.class);

            if (response == null || !"200".equals(String.valueOf(response.get("code")))) {
                String msg = response != null ? (String) response.get("msg") : "无响应";
                log.warn("PTZ start 失败 [{}/CH{}]: {}", deviceSerial, channelNo, msg);
                throw new RuntimeException("PTZ控制失败: " + msg);
            }
            log.debug("PTZ start [{}/CH{}] direction={} speed={}", deviceSerial, channelNo, direction, speed);
        } catch (Exception e) {
            log.error("PTZ start 异常 [{}/CH{}]: {}", deviceSerial, channelNo, e.getMessage());
            throw new RuntimeException("PTZ控制失败: " + e.getMessage());
        }
    }

    /**
     * 停止 PTZ 云台转动
     *
     * @param deviceSerial 设备序列号
     * @param channelNo    通道号
     */
    public void stopPtz(String deviceSerial, int channelNo) {
        String token = getAccessToken();
        String url = String.format(PTZ_STOP_URL, deviceSerial, channelNo);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("accessToken", token);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    url, new HttpEntity<>(body, headers), Map.class);

            if (response == null || !"200".equals(String.valueOf(response.get("code")))) {
                String msg = response != null ? (String) response.get("msg") : "无响应";
                log.warn("PTZ stop 失败 [{}/CH{}]: {}", deviceSerial, channelNo, msg);
                // 停止失败通常不需要抛异常，日志记录即可
            } else {
                log.debug("PTZ stop [{}/CH{}]", deviceSerial, channelNo);
            }
        } catch (Exception e) {
            log.error("PTZ stop 异常 [{}/CH{}]: {}", deviceSerial, channelNo, e.getMessage());
        }
    }

    @PostConstruct
    public void init() {
        if (!appKey.isEmpty() && !appSecret.isEmpty()) {
            try {
                getAccessToken();
                log.info("萤石云服务初始化成功");
                syncDevices();
            } catch (Exception e) {
                log.warn("萤石云服务初始化失败: {}", e.getMessage());
            }
        } else {
            log.warn("萤石云未配置 appKey/appSecret");
        }
    }
}
