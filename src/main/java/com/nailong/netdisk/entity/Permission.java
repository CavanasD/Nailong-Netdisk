package com.nailong.netdisk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_permission")
public class Permission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
}

