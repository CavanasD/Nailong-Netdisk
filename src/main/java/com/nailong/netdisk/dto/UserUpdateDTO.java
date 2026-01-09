package com.nailong.netdisk.dto;

import lombok.Data;

@Data
public class UserUpdateDTO {
    private Long userId;
    private String email;
    // 我们可以允许修改更多信息，但为了演示，只用 email 就足够了
}

