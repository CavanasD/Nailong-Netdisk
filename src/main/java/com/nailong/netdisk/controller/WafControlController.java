package com.nailong.netdisk.controller;

import com.nailong.netdisk.common.Result;
import com.nailong.netdisk.waf.IpBanService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/waf")
public class WafControlController {

    @Autowired
    private IpBanService ipBanService;

    @PostMapping("/unban")
    public Result<String> unban(HttpServletRequest request) {
        String ip = request == null ? null : request.getRemoteAddr();
        return Result.success("UNBANNED");
    }
}
