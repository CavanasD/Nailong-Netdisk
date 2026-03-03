package com.nailong.netdisk.controller;

import com.nailong.netdisk.common.Result;
import com.nailong.netdisk.dto.UserLoginDTO;
import com.nailong.netdisk.dto.UserRegisterDTO;
import com.nailong.netdisk.dto.UserUpdateDTO;
import com.nailong.netdisk.entity.User;
import com.nailong.netdisk.exception.WafBlockedException;
import com.nailong.netdisk.service.CaptchaAttemptService;
import com.nailong.netdisk.service.CaptchaService;
import com.nailong.netdisk.service.LoginAttemptService;
import com.nailong.netdisk.service.UserService;
import com.nailong.netdisk.utils.SecurityUtil;
import com.nailong.netdisk.waf.IpBanService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {

    private static final String BAN_RISK_NAME = "HEUR/Banned.IP.BadOperation";

    @Value("${security.vuln-mode:false}")
    private boolean vulnMode;

    @Autowired
    private UserService userService;

    @Autowired(required = false)
    private CaptchaService captchaService;

    @Autowired
    private CaptchaAttemptService captchaAttemptService;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private IpBanService ipBanService;

    @GetMapping("/me")
    public Result<Map<String, Object>> me(@RequestHeader(value = "token", required = false) String token) {
        if (token == null || token.isBlank()) {
            return Result.error("未登录");
        }
        String userIdStr = userService.getUserIdByToken(token);
        if (userIdStr == null) {
            return Result.error("登录已过期");
        }

        // 查库
        try {
            Long userId = Long.valueOf(userIdStr);
            com.nailong.netdisk.entity.User user = userService.getById(userId);
            if (user == null) {
                return Result.error("用户不存在");
            }

            // 脱敏返回
            Map<String, Object> map = java.util.HashMap.newHashMap(5);
            map.put("userId", user.getUserId());
            map.put("username", user.getUsername());
            map.put("email", user.getEmail());
            map.put("createTime", user.getCreateTime());
            map.put("role", user.getRole());
            map.put("storageUsed", user.getStorageUsed());
            map.put("storageQuota", user.getStorageQuota());
            map.put("avatarUrl", "/user/avatar/" + user.getUserId());

            return Result.success(map);
        } catch (NumberFormatException e) {
            return Result.error("用户ID无效");
        }
    }
    @PostMapping("/register")
    public Result<String> register(HttpServletRequest request, @RequestBody @Validated UserRegisterDTO registerDTO) {
        if (captchaService == null) {
            return Result.error("验证码服务不可用");
        }

        String ip = request == null ? "UNKNOWN" : request.getRemoteAddr();
        String key = ip + ":" + (registerDTO.getUsername() == null ? "" : registerDTO.getUsername());

        // If already banned, block immediately
        IpBanService.BanStatus banStatus = ipBanService.getBanStatus(ip);
        if (banStatus.banned()) {
            String userId = "ANONYMOUS";
            String secId = SecurityUtil.generateSecID(BAN_RISK_NAME, userId);
            String details = "BANNED，请停止不当行为，剩余时间：" + banStatus.remainingSeconds() + "秒";
            throw new WafBlockedException(secId, userId, BAN_RISK_NAME, true, banStatus.remainingSeconds(), details);
        }

        boolean ok;
        if (vulnMode && "114514".equals(registerDTO.getCaptchaAnswer())) {
            ok = true;
        } else {
            ok = captchaService.verify(registerDTO.getCaptchaId(), registerDTO.getCaptchaAnswer());
        }
        if (!ok) {
            int failCount = captchaAttemptService.incrementAndGet(key);
            if (failCount >= 3) {
                ipBanService.banIp(ip);
                IpBanService.BanStatus after = ipBanService.getBanStatus(ip);
                String userId = "ANONYMOUS";
                String secId = SecurityUtil.generateSecID(BAN_RISK_NAME, userId);
                // optional audit log
                SecurityUtil.logSecurityEvent(secId, "{\"riskName\":\"" + BAN_RISK_NAME + "\",\"userId\":\"" + userId + "\",\"details\":\"captcha failed 3 times\",\"ip\":\"" + ip + "\"}");
                String details = "BANNED，请停止不当行为，剩余时间：" + after.remainingSeconds() + "秒";
                throw new WafBlockedException(secId, userId, BAN_RISK_NAME, true, after.remainingSeconds(), details);
            }
            return Result.error("验证码错误（" + failCount + "/3）");
        }

        captchaAttemptService.reset(key);
        userService.register(registerDTO);
        return Result.success();
    }

    @PostMapping("/login")
    public Object login(HttpServletRequest request, @RequestBody @Validated UserLoginDTO loginDTO) {
        String ip = request == null ? "UNKNOWN" : request.getRemoteAddr();
        String username = loginDTO == null ? "" : (loginDTO.getUsername() == null ? "" : loginDTO.getUsername());
        String key = ip + ":" + username;

        IpBanService.BanStatus banStatus = ipBanService.getBanStatus(ip);
        if (banStatus.banned()) {
            String userId = "ANONYMOUS";
            String secId = SecurityUtil.generateSecID(BAN_RISK_NAME, userId);
            String details = "BANNED，请停止不当行为，剩余时间：" + banStatus.remainingSeconds() + "秒";
            throw new WafBlockedException(secId, userId, BAN_RISK_NAME, true, banStatus.remainingSeconds(), details);
        }

        try {
            String token = userService.login(loginDTO);
            loginAttemptService.reset(key);
            return Result.success(token);
        } catch (Exception e) {
            int failCount = loginAttemptService.incrementAndGet(key);
            if (failCount >= 5) {
                ipBanService.banIp(ip);
                IpBanService.BanStatus after = ipBanService.getBanStatus(ip);
                String userId = "ANONYMOUS";
                String secId = SecurityUtil.generateSecID(BAN_RISK_NAME, userId);
                SecurityUtil.logSecurityEvent(secId, "{\"riskName\":\"" + BAN_RISK_NAME + "\",\"userId\":\"" + userId + "\",\"details\":\"login failed 5 times\",\"ip\":\"" + ip + "\"}");
                String details = "BANNED，请停止不当行为，剩余时间：" + after.remainingSeconds() + "秒";
                throw new WafBlockedException(secId, userId, BAN_RISK_NAME, true, after.remainingSeconds(), details);
            }
            // keep generic message but show attempt count to user
            return Result.error("用户名或密码错误（" + failCount + "/5）");
        }
    }

    @GetMapping("/register")
    public Result<String> registerPageHint() {
        return Result.error("请使用 POST 提交注册信息（JSON）");
    }

    @GetMapping("/login")
    public Result<String> loginPageHint() {
        return Result.error("请使用 POST 提交登录信息（JSON）");
    }

    @PutMapping("/update")
    public Result<Void> updateUser(@RequestHeader(value = "token", required = false) String token, @RequestBody UserUpdateDTO userUpdateDTO) {
        if (token == null || token.isBlank()) {
            return Result.error("未登录");
        }
        String userIdStr = userService.getUserIdByToken(token);
        if (userIdStr == null) {
            return Result.error("登录已过期");
        }

        User userToUpdate = new User();
        // 强制使用 Token 解析出的 ID，防止越权
        userToUpdate.setUserId(Long.valueOf(userIdStr));
        userToUpdate.setEmail(userUpdateDTO.getEmail());

        // 注意：这里仍然需要在 Service 层或 Entity 配置中处理，确保只更新非空字段，否则可能会把其他字段置空
        // 假设 MyBatis Plus 配置了 ignoring null values
        boolean success = userService.updateById(userToUpdate);

        if (success) {
            return Result.success();
        } else {
            return Result.error("更新失败，用户可能不存在");
        }
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> uploadAvatar(@RequestHeader(value = "token", required = false) String token,
                                       @RequestPart("file") MultipartFile file) throws IOException {
        if (token == null || token.isBlank()) {
            return Result.error("未登录");
        }
        String userIdStr = userService.getUserIdByToken(token);
        if (userIdStr == null) {
            return Result.error("登录已过期");
        }
        Long userId;
        try {
            userId = Long.valueOf(userIdStr);
        } catch (Exception e) {
            return Result.error("用户ID无效");
        }

        if (file == null || file.isEmpty()) {
            return Result.error("请选择头像文件");
        }

        // basic size limit (5MB)
        if (file.getSize() > 5L * 1024 * 1024) {
            return Result.error("头像过大（最大 5MB）");
        }

        String contentType = file.getContentType();
        if (contentType == null || !(contentType.startsWith("image/"))) {
            return Result.error("仅支持图片类型头像");
        }

        Path baseDir = Paths.get("uploaded_files", "avatars");
        Files.createDirectories(baseDir);

        // store as png if frontend sends png; otherwise keep original extension best-effort
        String ext = ".png";
        String originalName = file.getOriginalFilename();
        if (originalName != null) {
            int dot = originalName.lastIndexOf('.');
            if (dot > -1 && dot < originalName.length() - 1) {
                String candidate = originalName.substring(dot);
                if (candidate.length() <= 8) {
                    ext = candidate;
                }
            }
        }

        String storedName = "u" + userId + "_" + System.currentTimeMillis() + ext;
        Path target = baseDir.resolve(storedName);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        User u = new User();
        u.setUserId(userId);
        u.setAvatarPath(target.toAbsolutePath().toString());
        userService.updateById(u);

        return Result.success("/user/avatar/" + userId);
    }

    @GetMapping("/avatar/{userId}")
    public ResponseEntity<Resource> getAvatar(@PathVariable("userId") Long userId) {
        if (userId == null) {
            return ResponseEntity.notFound().build();
        }
        User user = userService.getById(userId);
        if (user == null || user.getAvatarPath() == null || user.getAvatarPath().isBlank()) {
            return ResponseEntity.notFound().build();
        }
        Path path = Paths.get(user.getAvatarPath());
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            String probed = Files.probeContentType(path);
            if (probed != null) {
                mediaType = MediaType.parseMediaType(probed);
            }
        } catch (Exception ignored) {
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .contentType(mediaType)
                .body(new FileSystemResource(path));
    }

    /**
     * 按用户名搜索用户（存在 SQL 注入漏洞）
     * @param username 用户名
     * @return 用户列表
     */
    @GetMapping("/search")
    public Result<java.util.List<User>> searchUsers(@RequestParam String username, @RequestParam(defaultValue = "user_id") String order) {
        java.util.List<User> users = userService.searchByUsername(username, order);
        return Result.success(users);
    }
}
