package com.nailong.netdisk.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nailong.netdisk.common.Result;
import com.nailong.netdisk.dto.FileInfoDTO;
import com.nailong.netdisk.entity.StoredFile;
import com.nailong.netdisk.entity.User;
import com.nailong.netdisk.mapper.UserMapper;
import com.nailong.netdisk.service.StoredFileService;
import com.nailong.netdisk.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/file")
public class FileController {

    private static final long DEFAULT_QUOTA_BYTES = 200L * 1024 * 1024;
    private static final int TRASH_RETAIN_DAYS = 30;
    private static final Path BASE_UPLOAD_DIR = Paths.get("uploaded_files").toAbsolutePath().normalize();

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

        Path userDir = resolveUserDir(userId);
        if (userDir == null) {
            return Result.error("非法用户路径");
        }
        Files.createDirectories(userDir);

        Path target = userDir.resolve(storedName).normalize();
        if (!target.startsWith(userDir)) {
            return Result.error("非法文件路径");
        }
        Files.copy(file.getInputStream(), target);

        StoredFile storedFile = new StoredFile();
        storedFile.setUserId(userId);
        storedFile.setOriginalName(originalName);
        storedFile.setStoredName(storedName);
        storedFile.setContentType(file.getContentType());
        storedFile.setSize(size);
        storedFile.setStoragePath(target.toAbsolutePath().toString());
        storedFile.setCreateTime(LocalDateTime.now());
        storedFile.setTrashed(0);

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
                .eq(StoredFile::getTrashed, 0)
                .orderByDesc(StoredFile::getCreateTime));

        return Result.success(files.stream().map(this::toDto).collect(Collectors.toList()));
    }

    @GetMapping("/trash")
    public Result<List<FileInfoDTO>> listTrash(@RequestHeader(value = "token", required = false) String token) {
        Long userId = requireUserId(token);

        List<StoredFile> files = storedFileService.list(new LambdaQueryWrapper<StoredFile>()
                .eq(StoredFile::getUserId, userId)
                .eq(StoredFile::getTrashed, 1)
                .orderByDesc(StoredFile::getTrashTime));

        return Result.success(files.stream().map(this::toDto).collect(Collectors.toList()));
    }

    @DeleteMapping("/{fileId}")
    @Transactional
    public Result<String> moveToTrash(@PathVariable("fileId") Long fileId,
                                      @RequestHeader(value = "token", required = false) String token) {
        Long userId = requireUserId(token);
        StoredFile file = requireOwnedFile(fileId, userId);

        if (file.getTrashed() != null && file.getTrashed() == 1) {
            return Result.success("文件已在回收站");
        }

        LocalDateTime now = LocalDateTime.now();
        file.setTrashed(1);
        file.setTrashTime(now);
        file.setExpireTime(now.plusDays(TRASH_RETAIN_DAYS));
        file.setTrashedBy(userId);
        storedFileService.updateById(file);

        return Result.success("已移入回收站");
    }

    @PostMapping("/restore/{fileId}")
    @Transactional
    public Result<String> restore(@PathVariable("fileId") Long fileId,
                                  @RequestHeader(value = "token", required = false) String token) {
        Long userId = requireUserId(token);
        StoredFile file = requireOwnedFile(fileId, userId);

        if (file.getTrashed() == null || file.getTrashed() == 0) {
            return Result.success("文件不在回收站");
        }
        if (file.getExpireTime() != null && file.getExpireTime().isBefore(LocalDateTime.now())) {
            return Result.error("文件已过期，无法恢复，请彻底删除");
        }

        file.setTrashed(0);
        file.setTrashTime(null);
        file.setExpireTime(null);
        file.setTrashedBy(null);
        storedFileService.updateById(file);

        return Result.success("恢复成功");
    }

    @DeleteMapping("/{fileId}/purge")
    @Transactional
    public Result<String> purge(@PathVariable("fileId") Long fileId,
                                @RequestHeader(value = "token", required = false) String token) {
        Long userId = requireUserId(token);
        StoredFile file = requireOwnedFile(fileId, userId);

        if (file.getTrashed() == null || file.getTrashed() == 0) {
            return Result.error("请先将文件移入回收站");
        }

        Path path = safeResolveStoredPath(file.getStoragePath());
        if (path == null) {
            return Result.error("非法文件路径");
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            return Result.error("删除物理文件失败：" + e.getMessage());
        }

        storedFileService.removeById(file.getId());

        long fileSize = file.getSize() == null ? 0L : file.getSize();
        jdbcTemplate.update("UPDATE sys_user SET storage_used = GREATEST(COALESCE(storage_used,0) - ?, 0) WHERE user_id = ?", fileSize, userId);

        return Result.success("彻底删除成功");
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable("fileId") Long fileId,
                                             @RequestHeader(value = "token", required = false) String token,
                                             @RequestParam(value = "token", required = false) String tokenQuery) {
        String effectiveToken = StringUtils.hasText(token) ? token : tokenQuery;
        Long userId = requireUserId(effectiveToken);

        StoredFile storedFile = storedFileService.getById(fileId);
        if (storedFile == null || !userId.equals(storedFile.getUserId()) || (storedFile.getTrashed() != null && storedFile.getTrashed() == 1)) {
            return ResponseEntity.status(404).build();
        }

        Path path = safeResolveStoredPath(storedFile.getStoragePath());
        if (path == null || !Files.exists(path)) {
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

    private StoredFile requireOwnedFile(Long fileId, Long userId) {
        StoredFile file = storedFileService.getById(fileId);
        if (file == null || !userId.equals(file.getUserId())) {
            throw new RuntimeException("文件不存在或无权限");
        }
        return file;
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
        dto.setTrashed(file.getTrashed());
        dto.setTrashTime(file.getTrashTime());
        dto.setExpireTime(file.getExpireTime());
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

    private Path resolveUserDir(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        String userFolder = String.valueOf(userId);
        if (!userFolder.matches("\\d+")) {
            return null;
        }
        Path userDir = BASE_UPLOAD_DIR.resolve(userFolder).normalize();
        if (!userDir.startsWith(BASE_UPLOAD_DIR)) {
            return null;
        }
        return userDir;
    }

    private Path safeResolveStoredPath(String storedPath) {
        if (!StringUtils.hasText(storedPath)) {
            return null;
        }
        Path path = Paths.get(storedPath).toAbsolutePath().normalize();
        if (!path.startsWith(BASE_UPLOAD_DIR)) {
            return null;
        }
        return path;
    }
}
