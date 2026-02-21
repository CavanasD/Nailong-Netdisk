package com.nailong.netdisk.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nailong.netdisk.dto.UserLoginDTO;
import com.nailong.netdisk.dto.UserRegisterDTO;
import com.nailong.netdisk.entity.User;

public interface UserService extends IService<User> {
    void register(UserRegisterDTO registerDTO);
    String login(UserLoginDTO loginDTO);
    String getUserIdByToken(String token);
    java.util.List<User> searchByUsername(String username, String order);
    User getCurrentUser(String token);

    /**
     * Get user details including roles and permissions.
     * @param userId user id
     * @return User with populated roles and permissions lists
     */
    User getUserWithRoles(Long userId);

    /**
     * Check if user has specific permission
     * @param userId user id
     * @param permission permission string (e.g. "user:list")
     * @return true if authorized
     */
    boolean hasPermission(Long userId, String permission);

    /**
     * Check if user has specific role
     * @param userId user id
     * @param roleCode role name (e.g. "ADMIN")
     * @return true if authorized
     */
    boolean hasRole(Long userId, String roleCode);
}
