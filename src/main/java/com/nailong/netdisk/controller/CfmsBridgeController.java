package com.nailong.netdisk.controller;

import com.nailong.netdisk.common.Result;
import com.nailong.netdisk.service.CfmsBridgeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/cfms")
public class CfmsBridgeController {

    private final CfmsBridgeService cfmsBridgeService;

    public CfmsBridgeController(CfmsBridgeService cfmsBridgeService) {
        this.cfmsBridgeService = cfmsBridgeService;
    }

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.success(cfmsBridgeService.health());
    }

    @GetMapping("/server-info")
    public Result<Map<String, Object>> serverInfo() {
        return Result.success(cfmsBridgeService.serverInfoStub());
    }
}

