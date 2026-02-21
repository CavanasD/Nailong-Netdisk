package com.nailong.netdisk.waf;

import com.nailong.netdisk.utils.SecurityUtil;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

public final class WafBlockUtil {

    private WafBlockUtil() {
    }

    public static ResponseEntity<Map<String, Object>> banned(String userId, String riskName, String details, long remainingSeconds) {
        String uid = (userId == null || userId.isBlank()) ? "ANONYMOUS" : userId;
        String secId = SecurityUtil.generateSecID(riskName, uid);

        Map<String, Object> data = new HashMap<>();
        data.put("secId", secId);
        data.put("userId", uid);
        data.put("riskName", riskName);
        data.put("banned", true);
        data.put("remainSeconds", remainingSeconds);
        if (details != null) {
            data.put("details", details);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("code", 400);
        body.put("message", "Nailong Defender Warning! Hacker detected");
        body.put("data", data);

        return ResponseEntity.status(400)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
