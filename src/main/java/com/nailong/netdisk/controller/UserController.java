package com.nailong.netdisk.controller;

import com.nailong.netdisk.common.Result;
import com.nailong.netdisk.dto.UserLoginDTO;
import com.nailong.netdisk.dto.UserRegisterDTO;
import com.nailong.netdisk.dto.UserUpdateDTO;
import com.nailong.netdisk.entity.User;
import com.nailong.netdisk.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private UserService userService;

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
    public Result<String> register(@RequestBody @Validated UserRegisterDTO registerDTO) {
        try {
            userService.register(registerDTO);
            return Result.success();
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/login")
    public Result<String> login(@RequestBody @Validated UserLoginDTO loginDTO) {
        try {
            String token = userService.login(loginDTO);
            return Result.success(token);
        } catch (Exception e) {
            return Result.error(e.getMessage());
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
