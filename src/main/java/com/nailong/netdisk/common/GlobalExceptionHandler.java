package com.nailong.netdisk.common;

import com.nailong.netdisk.exception.WafBlockedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WafBlockedException.class)
    public ResponseEntity<Map<String, Object>> handleWafBlocked(WafBlockedException e) {
        Map<String, Object> data = new HashMap<>();
        data.put("secId", e.getSecId());
        data.put("userId", e.getUserId());
        data.put("riskName", e.getRiskName());

        if (e.isBanned()) {
            data.put("banned", true);
            data.put("remainSeconds", e.getRemainSeconds());
        }
        if (e.getDetails() != null) {
            data.put("details", e.getDetails());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("code", 400);
        body.put("message", "Nailong Defender Warning! Hacker detected");
        body.put("data", data);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {

        return Result.error(e.getMessage()); // 暂时先返回 Message 方便调试，后续可改为 "系统繁忙"
    }

    @ExceptionHandler(RuntimeException.class)
    public Result<String> handleRuntimeException(RuntimeException e) {
        return Result.error(e.getMessage());
    }
}

