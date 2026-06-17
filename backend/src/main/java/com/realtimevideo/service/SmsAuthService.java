package com.realtimevideo.service;

import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 阿里云短信认证服务
 *
 * 免资质、免签名、免模板，直接调 API 发送和核验短信验证码。
 * 使用 aliyun-java-sdk-core 的 CommonRequest 方式调用，无需引入 dypnsapi 专用 SDK。
 */
@Slf4j
@Service
public class SmsAuthService {

    @Value("${sms-auth.access-key-id}")
    private String accessKeyId;

    @Value("${sms-auth.access-key-secret}")
    private String accessKeySecret;

    @Value("${sms-auth.sign-name}")
    private String signName;

    @Value("${sms-auth.template-code}")
    private String templateCode;

    private static final String REGION_ID = "cn-hangzhou";
    private static final String DOMAIN = "dypnsapi.aliyuncs.com";
    private static final String VERSION = "2017-05-25";
    private static final int CODE_VALID_TIME = 300;
    private static final int SEND_INTERVAL = 60;

    private IAcsClient client;

    @PostConstruct
    public void init() {
        if (accessKeyId == null || accessKeyId.isEmpty()
                || accessKeySecret == null || accessKeySecret.isEmpty()) {
            log.warn("阿里云短信认证服务未配置 accessKey，短信功能不可用");
            return;
        }
        try {
            DefaultProfile profile = DefaultProfile.getProfile(REGION_ID, accessKeyId, accessKeySecret);
            this.client = new DefaultAcsClient(profile);
            log.info("阿里云短信认证服务初始化成功，签名: {}", signName);
        } catch (Exception e) {
            log.error("阿里云短信认证服务初始化失败", e);
        }
    }

    /**
     * 发送短信验证码
     */
    public SendResult sendCode(String phoneNumber) {
        if (client == null) {
            return SendResult.fail("短信服务未配置");
        }

        try {
            CommonRequest request = new CommonRequest();
            request.setSysMethod(MethodType.POST);
            request.setSysDomain(DOMAIN);
            request.setSysVersion(VERSION);
            request.setSysAction("SendSmsVerifyCode");
            request.putQueryParameter("PhoneNumber", phoneNumber);
            request.putQueryParameter("SignName", signName);
            request.putQueryParameter("TemplateCode", templateCode);
            request.putQueryParameter("TemplateParam", "{\"code\":\"##code##\",\"min\":\"5\"}");
            request.putQueryParameter("ValidTime", String.valueOf(CODE_VALID_TIME));
            request.putQueryParameter("Interval", String.valueOf(SEND_INTERVAL));
            request.putQueryParameter("CodeLength", "4");
            request.putQueryParameter("CodeType", "1");
            request.putQueryParameter("ReturnVerifyCode", "true");

            CommonResponse response = client.getCommonResponse(request);

            log.info("发送验证码 phone={}, response={}", phoneNumber, response.getData());

            // 解析响应
            if (response.getHttpStatus() == 200) {
                String data = response.getData();
                // 简单的 JSON 解析，提取 verifyCode
                String verifyCode = extractJsonValue(data, "VerifyCode");
                String bizId = extractJsonValue(data, "BizId");
                String code = extractJsonValue(data, "Code");
                if ("OK".equals(code)) {
                    return SendResult.ok(verifyCode, bizId);
                }
                String msg = extractJsonValue(data, "Message");
                return SendResult.fail(msg != null ? msg : "发送失败");
            }
            return SendResult.fail("发送失败，HTTP " + response.getHttpStatus());
        } catch (Exception e) {
            log.error("发送验证码失败 phone={}", phoneNumber, e);
            return SendResult.fail("发送失败: " + e.getMessage());
        }
    }

    /**
     * 核验短信验证码
     */
    public boolean checkCode(String phoneNumber, String verifyCode) {
        if (client == null) {
            return false;
        }

        try {
            CommonRequest request = new CommonRequest();
            request.setSysMethod(MethodType.POST);
            request.setSysDomain(DOMAIN);
            request.setSysVersion(VERSION);
            request.setSysAction("CheckSmsVerifyCode");
            request.putQueryParameter("PhoneNumber", phoneNumber);
            request.putQueryParameter("VerifyCode", verifyCode);
            request.putQueryParameter("CaseAuthPolicy", "1");

            CommonResponse response = client.getCommonResponse(request);

            log.info("核验验证码 phone={}, response={}", phoneNumber, response.getData());

            if (response.getHttpStatus() == 200) {
                String data = response.getData();
                String code = extractJsonValue(data, "Code");
                String verifyResult = extractJsonValue(data, "VerifyResult");
                return "OK".equals(code) && "PASS".equals(verifyResult);
            }
            return false;
        } catch (Exception e) {
            log.error("核验验证码失败 phone={}", phoneNumber, e);
            return false;
        }
    }

    /**
     * 简单 JSON 字段提取（无第三方依赖）
     */
    private String extractJsonValue(String json, String key) {
        if (json == null || key == null) return null;
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) {
            // 尝试不带引号的值
            search = "\"" + key + "\":";
            start = json.indexOf(search);
            if (start < 0) return null;
            start += search.length();
            int end = json.indexOf(",", start);
            if (end < 0) end = json.indexOf("}", start);
            if (end < 0) return null;
            return json.substring(start, end).trim();
        }
        start += search.length();
        int end = json.indexOf("\"", start);
        return end > start ? json.substring(start, end) : null;
    }

    public record SendResult(boolean success, String verifyCode, String bizId, String message) {
        static SendResult ok(String verifyCode, String bizId) {
            return new SendResult(true, verifyCode, bizId, null);
        }
        static SendResult fail(String message) {
            return new SendResult(false, null, null, message);
        }
    }
}
