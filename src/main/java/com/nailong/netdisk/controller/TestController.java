package com.nailong.netdisk.controller;

import com.nailong.netdisk.entity.User;
import com.nailong.netdisk.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TestController {
    @Autowired
    private UserMapper userMapper;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @GetMapping("/hello")
    public String hello(){
        return "hello";

    }

    @GetMapping("/user/list")
    public List<User> listUsers() {
        return userMapper.selectList(null);
    }

    @GetMapping("/redis/ping")
    public String redisPing() {
        if (redisTemplate == null) return "redisTemplate not present";
        try {
            String key = "ping:test";
            redisTemplate.opsForValue().set(key, "1");
            return "OK";
        } catch (Exception e) {
            return "FAIL: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }
}
