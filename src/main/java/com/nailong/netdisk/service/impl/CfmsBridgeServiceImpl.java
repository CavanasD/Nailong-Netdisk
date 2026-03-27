package com.nailong.netdisk.service.impl;

import com.nailong.netdisk.config.CfmsBridgeProperties;
import com.nailong.netdisk.service.CfmsBridgeService;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CfmsBridgeServiceImpl implements CfmsBridgeService {

    private final CfmsBridgeProperties properties;

    public CfmsBridgeServiceImpl(CfmsBridgeProperties properties) {
        this.properties = properties;
    }

    @Override
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", properties.isEnabled());
        result.put("host", properties.getHost());
        result.put("port", properties.getPort());

        if (!properties.isEnabled()) {
            result.put("status", "DISABLED");
            return result;
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(properties.getHost(), properties.getPort()), properties.getConnectTimeout());
            result.put("status", "UP");
        } catch (Exception e) {
            result.put("status", "DOWN");
            result.put("error", e.getMessage());
        }

        return result;
    }

    @Override
    public Map<String, Object> serverInfoStub() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bridge", "cfms");
        result.put("mode", "stub");
        result.put("target", properties.getHost() + ":" + properties.getPort());
        result.put("message", "CFMS bridge skeleton is ready. Next step: implement websocket action 'server_info'.");
        return result;
    }
}

