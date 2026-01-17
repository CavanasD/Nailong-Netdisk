package com.nailong.netdisk.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nailong.netdisk.common.Result;
import com.nailong.netdisk.entity.User;
import com.nailong.netdisk.service.UserService;
import com.nailong.netdisk.utils.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/admin")
public class SecurityAdminController {

    @Autowired
    private UserService userService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private boolean isSuperAdmin(User user) {
        // ID 8 is super admin
        return user != null && (user.getUserId() == 8 || "SUPER_ADMIN".equals(user.getRole()));
    }

    private boolean isAdmin(User user) {
        return user != null && ("ADMIN".equals(user.getRole()) || isSuperAdmin(user));
    }

    @GetMapping("/logs")
    public Result<List<Map<String, Object>>> getSecurityLogs(@RequestHeader("token") String token) {
        User user = userService.getCurrentUser(token);
        if (!isAdmin(user)) {
             return Result.error("Permission Denied: Admins Only");
        }

        List<Map<String, Object>> logs = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader("waf_events.log"))) {
            String line;
            while ((line = br.readLine()) != null) {
                // Parse line: Timestamp [SEC_EVENT] ID:xxx Content:xxx
                if (line.contains("ID:")) {
                    Map<String, Object> logEntry = new HashMap<>();
                    String[] parts = line.split("ID:", 2);
                    String timestamp = parts[0].split("\\[SEC_EVENT]")[0].trim();
                    logEntry.put("timestamp", timestamp);

                    String remaining = parts[1];
                    String[] contentParts = remaining.split("Content:", 2);
                    String secId = contentParts[0].trim();
                    String contentRaw = contentParts.length > 1 ? contentParts[1].trim() : "";

                    logEntry.put("secId", secId);

                    // Decrypt SecID
                    String decrypted = SecurityUtil.decryptSecID(secId);
                    logEntry.put("decryptedInfo", decrypted);

                    // Try to parse contentRaw as JSON
                    try {
                        if (contentRaw.startsWith("{")) {
                            Map<String, Object> details = objectMapper.readValue(contentRaw, Map.class);
                            logEntry.put("details", details); // detailed object
                            logEntry.put("summary", details.get("details"));
                        } else {
                             logEntry.put("summary", contentRaw);
                             logEntry.put("details", contentRaw);
                        }
                    } catch (Exception e) {
                        logEntry.put("summary", contentRaw);
                         logEntry.put("details", contentRaw);
                    }

                    logs.add(logEntry);
                }
            }
        } catch (IOException e) {
            // File might not exist yet
        }
        // Reverse logs to show newest first
        java.util.Collections.reverse(logs);
        return Result.success(logs);
    }

    @GetMapping("/users")
    public Result<List<User>> listUsers(@RequestHeader("token") String token) {
        User user = userService.getCurrentUser(token);
        if (!isAdmin(user)) {
            return Result.error("Permission Denied: Admins Only");
        }
        // Don't leak passwords in real app, but this is demo
        return Result.success(userService.list());
    }

    @PostMapping("/promote")
    public Result<String> promoteAdmin(@RequestHeader("token") String token, @RequestBody Map<String, Long> payload) {
        User user = userService.getCurrentUser(token);
        if (!isSuperAdmin(user)) {
            return Result.error("Permission Denied: Super Admin Only");
        }

        Long targetUserId = payload.get("userId");
        User target = userService.getById(targetUserId);
        if (target != null) {
            target.setRole("ADMIN");
            userService.updateById(target);
            return Result.success("Promoted user " + targetUserId + " to ADMIN");
        }
        return Result.error("User not found");
    }
}

