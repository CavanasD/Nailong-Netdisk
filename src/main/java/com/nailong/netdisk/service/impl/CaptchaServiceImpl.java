package com.nailong.netdisk.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nailong.netdisk.service.CaptchaService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.Map;

@Service
public class CaptchaServiceImpl implements CaptchaService {

    private static final String API_URL = "https://v2.xxapi.cn/api/chineseCaptcha";

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CaptchaServiceImpl() {
        this.restClient = RestClient.create();
    }

    @Override
    public Map<String, String> generate() {
        String body = restClient.get()
            .uri(API_URL)
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(String.class);

        Map<String, String> result = new HashMap<>();
        try {
            JsonNode root = objectMapper.readTree(body);
            int code = root.path("code").asInt(-1);
            if (code != 200) {
                return result;
            }
            JsonNode data = root.path("data");
            String id = data.path("id").asText("");
            String url = data.path("url").asText("");
            if (!id.isBlank() && !url.isBlank()) {
                result.put("id", id);
                result.put("url", url);
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    @Override
    public boolean verify(String id, String answer) {
        if (id == null || id.isBlank() || answer == null || answer.isBlank()) {
            return false;
        }

        String body;
        try {
                String url = UriComponentsBuilder.fromUriString(API_URL)
                .queryParam("type", "verify")
                .queryParam("id", id)
                .queryParam("answer", answer)
                    .build()
                    .encode()
                    .toUriString();

            body = restClient.get()
                .uri(url)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);
        } catch (Exception e) {
            return false;
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            int code = root.path("code").asInt(-1);
            String msg = root.path("msg").asText("");
            JsonNode data = root.path("data");

            if (code != 200) {
                return false;
            }

            // Try strict interpretations first
            if (data.isBoolean()) {
                return data.asBoolean(false);
            }
            if (data.isInt() || data.isLong()) {
                return data.asLong(0) == 1;
            }
            if (data.isTextual()) {
                String v = data.asText("").trim().toLowerCase();
                if ("true".equals(v) || "success".equals(v) || "ok".equals(v) || "1".equals(v)) {
                    return true;
                }
                if ("false".equals(v) || "fail".equals(v) || "0".equals(v)) {
                    return false;
                }
            }
            if (data.isObject()) {
                JsonNode r1 = data.get("result");
                if (r1 != null && r1.isBoolean()) {
                    return r1.asBoolean(false);
                }
                JsonNode r2 = data.get("success");
                if (r2 != null && r2.isBoolean()) {
                    return r2.asBoolean(false);
                }
            }

            // Fallback: use msg heuristics (avoid false-positive on failure)
            String msgLower = msg.toLowerCase();
            if (msg.contains("失败") || msg.contains("错误") || msgLower.contains("fail") || msgLower.contains("error")) {
                return false;
            }
            return msg.contains("成功") || msgLower.contains("success") || msgLower.contains("ok");
        } catch (Exception e) {
            return false;
        }
    }
}
