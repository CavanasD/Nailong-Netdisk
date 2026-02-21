package com.nailong.netdisk.waf;

import com.nailong.netdisk.config.CachedBodyHttpServletRequest;
import com.nailong.netdisk.entity.User;
import com.nailong.netdisk.service.UserService;
import com.nailong.netdisk.utils.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.regex.Pattern;
import java.util.Map;
import java.util.HashMap;
import com.fasterxml.jackson.databind.ObjectMapper;

public class NailongDefenderInterceptor implements HandlerInterceptor {

    private final UserService userService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NailongDefenderInterceptor(UserService userService) {
        this.userService = userService;
    }

    // 定义危险字符的正则表达式
    private static final Pattern[] DANGEROUS_PATTERNS = new Pattern[]{
            Pattern.compile("'", Pattern.CASE_INSENSITIVE),
            Pattern.compile("--", Pattern.CASE_INSENSITIVE),
            Pattern.compile(";", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)<script.*?>.*?</script.*?>"),
            Pattern.compile("(?i)<.*?javascript:.*?>"),
            Pattern.compile("(?i)onclick|onerror|onload|onmouseover", Pattern.CASE_INSENSITIVE)
    };

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取当前用户ID和对象
        String token = request.getHeader("token");
        String userId = "ANONYMOUS";
        User currentUser = null;

        if (token != null && userService != null) {
            currentUser = userService.getCurrentUser(token);
            if (currentUser != null) {
                userId = String.valueOf(currentUser.getUserId());
            } else {
                // 如果只取 ID
                String uid = userService.getUserIdByToken(token);
                if (uid != null) userId = uid;
            }
        }

        // 1. 检查后台管理权限 (Defense: AntiAdminFucker)
        String requestURI = request.getRequestURI();
        if (requestURI.startsWith("/admin")) {
            boolean isAdmin = false;
            if (currentUser != null) {
                String role = currentUser.getRole();
                // 简单判定：role 包含 ADMIN 或者是 ID 为 8 的用户
                if ("ADMIN".equals(role) || "SUPER_ADMIN".equals(role) || currentUser.getUserId() == 8) {
                    isAdmin = true;
                }
            }

            if (!isAdmin) {
                blockRequest(response, userId, "HEUR/Access.AntiAdminFucker.Gen", "Unauthorized admin access attempt", request);
                return false;
            }
            // 记录敏感操作日志: 管理员访问
            recordLog(userId, "AUDIT/Admin.Access", "Authorized admin access: " + requestURI, request);
        }

        // 2. 逻辑漏洞检查 (Defense: IDOR on User Update)
        // 专门针对 /user/update 接口，防止越权修改他人邮箱
        if ("/user/update".equals(requestURI) && "PUT".equalsIgnoreCase(request.getMethod())) {
            // Only check logineddd
            if (currentUser != null) {
                String payloadUserId = extractUserIdFromBody(request);
                if (payloadUserId != null) {
                    if (!userId.equals(payloadUserId)) {
                         boolean isAdmin = "ADMIN".equals(currentUser.getRole()) || "SUPER_ADMIN".equals(currentUser.getRole()) || currentUser.getUserId() == 8;
                         if (!isAdmin) {
                             blockRequest(response, userId, "HEUR/Logic.IDOR.UserUpdate", "Attempt to modify another user's data. Target ID: " + payloadUserId, request);
                             return false;
                         }
                    }
                }
            }
        }

        // 3. 注册/登录 输入检查 (Defense: Auth Input Validation)
        // 检测用户名和密码中是否包含非法危险字符
        if (("/user/register".equals(requestURI) || "/user/login".equals(requestURI)) && "POST".equalsIgnoreCase(request.getMethod())) {
            String body = getRequestBodyString(request);
            if (body != null) {
                String username = extractJsonStringValue(body, "username");
                if (isDangerous(username)) {
                    blockRequest(response, userId, "WAF/Auth.Input.DangerousChar", "Dangerous characters detected in username during auth.", request);
                    return false;
                }
                String password = extractJsonStringValue(body, "password");
                if (isDangerous(password)) {
                    blockRequest(response, userId, "WAF/Auth.Input.DangerousChar", "Dangerous characters detected in password during auth.", request);
                    return false;
                }
            }
        }

        // 4. SQL 注入 / XSS 检查 (URL Params)
        for (String[] paramValues : request.getParameterMap().values()) {
            for (String value : paramValues) {
                if (isDangerous(value)) {
                    blockRequest(response, userId, "WAF/Exploit.Generic.SQLi.A", "Payload: " + value, request);
                    return false;
                }
            }
        }

        // 5. Order 访问日志
        if (requestURI.toLowerCase().contains("order")) {
            recordLog(userId, "AUDIT/Order.Access", "Order related access: " + requestURI, request);
        }

        // 6. 敏感操作日志 (DELETE, PUT)
        if ("DELETE".equalsIgnoreCase(request.getMethod()) || "PUT".equalsIgnoreCase(request.getMethod())) {
            recordLog(userId, "AUDIT/Sensitive.Method", "Sensitive method usage: " + request.getMethod() + " on " + requestURI, request);
        }

        return true;
    }

    private void recordLog(String userId, String riskName, String details, HttpServletRequest request) {
        try {
            // 生成 SecID
            String secId = SecurityUtil.generateSecID(riskName, userId);

            // 收集详细请求信息
            Map<String, Object> logData = new HashMap<>();
            logData.put("riskName", riskName);
            logData.put("userId", userId);
            logData.put("details", details);
            logData.put("ip", request.getRemoteAddr());
            logData.put("method", request.getMethod());
            logData.put("uri", request.getRequestURI());
            logData.put("params", request.getParameterMap());

            // 尝试获取 Headers
            Map<String, String> headers = new HashMap<>();
            java.util.Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                headers.put(headerName, request.getHeader(headerName));
            }
            logData.put("headers", headers);

            // Body (if available)
            String body = getRequestBodyString(request);
            if (body != null && !body.isBlank()) {
                // Mask sensitive fields like password
                String maskedBody = body.replaceAll("(\"password\"\\s*:\\s*\")[^\"]*(\")", "$1******$2");
                logData.put("body", maskedBody);
            }

            String logJson = objectMapper.writeValueAsString(logData);

            // 记录日志 (确保 JSON 不换行)
            SecurityUtil.logSecurityEvent(secId, logJson.replace("\n", " ").replace("\r", " "));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void blockRequest(HttpServletResponse response, String userId, String riskName, String details, HttpServletRequest request) throws Exception {
        recordLog(userId, riskName, details, request);

        // 生成 SecID 用于返回
        String secId = SecurityUtil.generateSecID(riskName, userId);

        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        // 返回 JSON 包括 secId, userId, riskName
        response.getWriter().write(String.format(
                "{\"code\": 400, \"message\": \"Nailong Defender Warning! Hacker detected\", \"data\": {\"secId\": \"%s\", \"userId\": \"%s\", \"riskName\": \"%s\"}}",
                secId, userId, riskName));
    }

    private boolean isDangerous(String value) {
        if (value == null) {
            return false;
        }
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(value).find()) {
                return true;
            }
        }
        return false;
    }

    private String extractUserIdFromBody(HttpServletRequest request) {
        try {
            String body = getRequestBodyString(request);
            if (body == null || body.isBlank()) return null;

            // 简单解析 JSON 中的 userId
            // Body 示例: {"userId": 123, "email": "...}
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"userId\"\\s*:\\s*(\\d+)").matcher(body);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            // 解析失败忽略
        }
        return null;
    }

    private String getRequestBodyString(HttpServletRequest request) {
        try {
            if (request instanceof CachedBodyHttpServletRequest) {
                byte[] bytes = ((CachedBodyHttpServletRequest) request).getBody();
                return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private String extractJsonStringValue(String body, String key) {
        if (body == null) return null;
        try {
            // 匹配 "key" : "value" 格式
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {}
        return null;
    }
}
