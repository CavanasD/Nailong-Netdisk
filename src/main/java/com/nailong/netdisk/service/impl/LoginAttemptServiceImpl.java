package com.nailong.netdisk.service.impl;

import com.nailong.netdisk.service.LoginAttemptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptServiceImpl implements LoginAttemptService {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    public LoginAttemptServiceImpl(@Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private static final class LocalAttempt {
        private int count;
        private long expireAt;
    }

    private static final ConcurrentHashMap<String, LocalAttempt> LOCAL = new ConcurrentHashMap<>();

    @Override
    public int incrementAndGet(String key) {
        if (key == null || key.isBlank()) {
            key = "UNKNOWN";
        }

        String redisKey = "login:fail:" + key;
        if (redisTemplate != null) {
            try {
                Long val = redisTemplate.opsForValue().increment(redisKey);
                redisTemplate.expire(redisKey, TTL);
                return val == null ? 0 : val.intValue();
            } catch (Exception ignored) {
            }
        }

        long now = System.currentTimeMillis();
        LocalAttempt attempt = LOCAL.compute(redisKey, (k, v) -> {
            if (v == null || v.expireAt < now) {
                LocalAttempt n = new LocalAttempt();
                n.count = 1;
                n.expireAt = now + TTL.toMillis();
                return n;
            }
            v.count += 1;
            return v;
        });

        return attempt == null ? 0 : attempt.count;
    }

    @Override
    public void reset(String key) {
        if (key == null || key.isBlank()) {
            key = "UNKNOWN";
        }

        String redisKey = "login:fail:" + key;
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(redisKey);
            } catch (Exception ignored) {
            }
        }
        LOCAL.remove(redisKey);
    }
}
