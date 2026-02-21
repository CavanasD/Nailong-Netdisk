package com.nailong.netdisk.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FileInfoDTO {
    private Long id;
    private String originalName;
    private String contentType;
    private Long size;
    private LocalDateTime createTime;
}
