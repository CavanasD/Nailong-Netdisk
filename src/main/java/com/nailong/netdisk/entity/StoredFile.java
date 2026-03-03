package com.nailong.netdisk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_file")
public class StoredFile {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String originalName;

    private String storedName;

    private String contentType;

    private Long size;

    private String storagePath;

    private LocalDateTime createTime;

    private Integer trashed;

    private LocalDateTime trashTime;

    private LocalDateTime expireTime;

    private Long trashedBy;
}
