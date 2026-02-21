package com.nailong.netdisk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sys_user")
public class User {
    @TableId(type= IdType.AUTO)
    private Long userId;
    private String username;
    private String password;
    private String email;

    /**
     * Avatar local storage path (or future URL).
     */
    private String avatarPath;
    /**
     * @deprecated Use roles instead. This field is kept for backward compatibility and initial migration.
     */
    @Deprecated
    private String role;

    @TableField(exist = false)
    private List<Role> roles;

    @TableField(exist = false)
    private List<Permission> permissions;

    /**
     * Used storage in bytes.
     */
    private Long storageUsed;

    /**
     * Storage quota in bytes.
     */
    private Long storageQuota;

    private LocalDateTime createTime;
}
