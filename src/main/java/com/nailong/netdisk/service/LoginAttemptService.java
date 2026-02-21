package com.nailong.netdisk.service;

public interface LoginAttemptService {
    int incrementAndGet(String key);

    void reset(String key);
}
