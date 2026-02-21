package com.nailong.netdisk.service;

public interface CaptchaAttemptService {
    int incrementAndGet(String key);

    void reset(String key);
}
