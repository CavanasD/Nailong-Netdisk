package com.nailong.netdisk.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nailong.netdisk.common.Result;
import com.nailong.netdisk.dto.FileInfoDTO;
import com.nailong.netdisk.entity.StoredFile;
import com.nailong.netdisk.entity.User;
import com.nailong.netdisk.mapper.UserMapper;
import com.nailong.netdisk.service.StoredFileService;
import com.nailong.netdisk.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/file")
public class FileController {

    private static final long DEFAULT_QUOTA_BYTES = 200L * 1024 * 1024;

    @Value("${security.vuln-mode:false}")
    private boolean vulnMode;

    private final UserService userService;
    private final StoredFileService storedFileService;
    private final UserMapper userMapper;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public FileController(UserService userService,
                          StoredFileService storedFileService,
                          UserMapper userMapper,
                          JdbcTemplate jdbcTemplate) {
        this.userService = userService;
        this.storedFileService = storedFileService;
        this.userMapper = userMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<FileInfoDTO> upload(@RequestHeader(value = "token", required = false) String token,
                                      @RequestPart("file") MultipartFile file) throws IOException {
        Long userId = requireUserId(token);

        if (file == null || file.isEmpty()) {
            return Result.error("请选择文件");
        }

        long size = file.getSize();
        if (size <= 0) {
            return Result.error("文件大小无效");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        long used = user.getStorageUsed() == null ? 0L : user.getStorageUsed();
        long quota = user.getStorageQuota() == null ? DEFAULT_QUOTA_BYTES : user.getStorageQuota();

        if (used + size > quota) {
            return Result.error("空间不足：当前已用 " + formatBytes(used) + "，配额 " + formatBytes(quota) + "，本次上传 " + formatBytes(size));
        }

        String originalName = file.getOriginalFilename();
        if (!StringUtils.hasText(originalName)) {
            originalName = "file";
        }

        String ext = "";
        int dot = originalName.lastIndexOf('.');
        if (dot > -1 && dot < originalName.length() - 1) {
            ext = originalName.substring(dot);
        }

        String storedName = UUID.randomUUID() + ext;

        Path baseDir = Paths.get("uploaded_files");
        Path userDir = baseDir.resolve(String.valueOf(userId));
        Files.createDirectories(userDir);

        Path target = userDir.resolve(storedName);
        Files.copy(file.getInputStream(), target);

        StoredFile storedFile = new StoredFile();
        storedFile.setUserId(userId);
        storedFile.setOriginalName(originalName);
        storedFile.setStoredName(storedName);
        storedFile.setContentType(file.getContentType());
        storedFile.setSize(size);
        storedFile.setStoragePath(target.toAbsolutePath().toString());
        storedFile.setCreateTime(LocalDateTime.now());

        storedFileService.save(storedFile);

        // Atomic increment
        jdbcTemplate.update("UPDATE sys_user SET storage_used = COALESCE(storage_used,0) + ? WHERE user_id = ?", size, userId);

        return Result.success(toDto(storedFile));
    }

    @GetMapping("/list")
    public Result<List<FileInfoDTO>> listMyFiles(@RequestHeader(value = "token", required = false) String token) {
        Long userId = requireUserId(token);

        List<StoredFile> files = storedFileService.list(new LambdaQueryWrapper<StoredFile>()
                .eq(StoredFile::getUserId, userId)
                .orderByDesc(StoredFile::getCreateTime));

        return Result.success(files.stream().map(this::toDto).collect(Collectors.toList()));
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable("fileId") Long fileId,
                                             HttpServletRequest request,
                                             @RequestHeader(value = "token", required = false) String token,
                                             @RequestParam(value = "token", required = false) String tokenQuery) {
        StoredFile storedFile = storedFileService.getById(fileId);
        if (storedFile == null) {
            return ResponseEntity.status(404).build();
        }

        String share = request.getParameter("share");
        if (vulnMode && StringUtils.hasText(share)) {
            String expected = Base64.getUrlEncoder().encodeToString(
                    (storedFile.getId() + ":" + storedFile.getUserId()).getBytes(StandardCharsets.UTF_8));
            if (share.equals(expected)) {
                return buildDownloadResponse(storedFile);
            }
        }

        String effectiveToken = StringUtils.hasText(token) ? token : tokenQuery;
        Long userId = requireUserId(effectiveToken);

        if (!userId.equals(storedFile.getUserId())) {
            return ResponseEntity.status(404).build();
        }

        return buildDownloadResponse(storedFile);
    }

    private ResponseEntity<Resource> buildDownloadResponse(StoredFile storedFile) {
        Path path = Paths.get(storedFile.getStoragePath());
        if (!Files.exists(path)) {
            return ResponseEntity.status(404).build();
        }

        String filename = storedFile.getOriginalName();
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replaceAll("\\+", "%20");

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (StringUtils.hasText(storedFile.getContentType())) {
            try {
                mediaType = MediaType.parseMediaType(storedFile.getContentType());
            } catch (Exception ignored) {
            }
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .body(new FileSystemResource(path));
    }

    private Long requireUserId(String token) {
        if (!StringUtils.hasText(token)) {
            throw new RuntimeException("未登录");
        }
        String userIdStr = userService.getUserIdByToken(token);
        if (!StringUtils.hasText(userIdStr)) {
            throw new RuntimeException("登录已过期");
        }
        try {
            return Long.valueOf(userIdStr);
        } catch (Exception e) {
            throw new RuntimeException("用户ID无效");
        }
    }

    private FileInfoDTO toDto(StoredFile file) {
        FileInfoDTO dto = new FileInfoDTO();
        dto.setId(file.getId());
        dto.setOriginalName(file.getOriginalName());
        dto.setContentType(file.getContentType());
        dto.setSize(file.getSize());
        dto.setCreateTime(file.getCreateTime());
        return dto;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + "B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1fKB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1fMB", mb);
        double gb = mb / 1024.0;
        return String.format("%.2fGB", gb);
    }
}
