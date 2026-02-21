package com.nailong.netdisk.service;

import java.util.Map;

public interface CaptchaService {
    /**
     * Generate a new captcha.
     * @return map with keys: id, url (data:image/...;base64,...)
     */
    Map<String, String> generate();

    /**
     * Verify captcha answer.
     */
    boolean verify(String id, String answer);
}
