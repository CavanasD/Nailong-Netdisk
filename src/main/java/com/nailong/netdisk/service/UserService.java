package com.nailong.netdisk.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nailong.netdisk.dto.UserLoginDTO;
import com.nailong.netdisk.dto.UserRegisterDTO;
import com.nailong.netdisk.entity.User;

public interface UserService extends IService<User> {
    void register(UserRegisterDTO registerDTO);
    String login(UserLoginDTO loginDTO);
    String getUserIdByToken(String token);
}
