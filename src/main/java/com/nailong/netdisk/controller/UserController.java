package com.nailong.netdisk.controller;

import com.nailong.netdisk.common.Result;
import com.nailong.netdisk.dto.UserLoginDTO;
import com.nailong.netdisk.dto.UserRegisterDTO;
import com.nailong.netdisk.dto.UserUpdateDTO;
import com.nailong.netdisk.entity.User;
import com.nailong.netdisk.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/me")
    public Result<Map<String, Object>> me(@RequestHeader(value = "token", required = false) String token) {
        if (token == null || token.isBlank()) {
            return Result.error("未登录");
        }
        String userIdStr = userService.getUserIdByToken(token);
        if (userIdStr == null) {
            return Result.error("登录已过期");
        }

        // 查库
        try {
            Long userId = Long.valueOf(userIdStr);
            com.nailong.netdisk.entity.User user = userService.getById(userId);
            if (user == null) {
                return Result.error("用户不存在");
            }

            // 脱敏返回
            Map<String, Object> map = java.util.HashMap.newHashMap(5);
            map.put("userId", user.getUserId());
            map.put("username", user.getUsername());
            map.put("email", user.getEmail());
            map.put("createTime", user.getCreateTime());
            map.put("role", user.getRole());

            return Result.success(map);
        } catch (NumberFormatException e) {
            return Result.error("用户ID无效");
        }
    }
    @PostMapping("/register")
    public Result<String> register(@RequestBody @Validated UserRegisterDTO registerDTO) {
        try {
            userService.register(registerDTO);
            return Result.success();
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/login")
    public Result<String> login(@RequestBody @Validated UserLoginDTO loginDTO) {
        try {
            String token = userService.login(loginDTO);
            return Result.success(token);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/register")
    public Result<String> registerPageHint() {
        return Result.error("请使用 POST 提交注册信息（JSON）");
    }

    @GetMapping("/login")
    public Result<String> loginPageHint() {
        return Result.error("请使用 POST 提交登录信息（JSON）");
    }

    @PutMapping("/update")
    public Result<Void> updateUser(@RequestBody UserUpdateDTO userUpdateDTO) {
        // 漏洞所在：没有校验操作者身份，直接根据传入的 userId 进行更新
        // 正确的做法是：应该从 token 中获取当前登录用户的 ID，并以此为准进行更新
        User userToUpdate = new User();
        userToUpdate.setUserId(userUpdateDTO.getUserId());
        userToUpdate.setEmail(userUpdateDTO.getEmail());

        boolean success = userService.updateById(userToUpdate);

        if (success) {
            return Result.success();
        } else {
            return Result.error("更新失败，用户可能不存在");
        }
    }

    /**
     * 按用户名搜索用户（存在 SQL 注入漏洞）
     * @param username 用户名
     * @return 用户列表
     */
    @GetMapping("/search")
    public Result<java.util.List<User>> searchUsers(@RequestParam String username, @RequestParam(defaultValue = "user_id") String order) {
        java.util.List<User> users = userService.searchByUsername(username, order);
        return Result.success(users);
    }
}
