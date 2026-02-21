package com.nailong.netdisk.controller;

import com.nailong.netdisk.common.Result;
import com.nailong.netdisk.service.CaptchaService;
import com.nailong.netdisk.waf.IpBanService;
import com.nailong.netdisk.waf.WafBlockUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

@RestController
@RequestMapping("/captcha")
public class CaptchaController {

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private IpBanService ipBanService;

    @GetMapping("/new")
    public Object newCaptcha(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        IpBanService.BanStatus banStatus = ipBanService.getBanStatus(ip);
        if (banStatus.banned()) {
            String details = "BANNED，请停止不当行为，剩余时间：" + banStatus.remainingSeconds() + "秒";
            return WafBlockUtil.banned("ANONYMOUS", "HEUR/Banned.IP.BadOperation", details, banStatus.remainingSeconds());
        }

        Map<String, String> data = captchaService.generate();
        if (data == null || data.isEmpty()) {
            return Result.error("验证码服务不可用，请稍后再试");
        }
        return Result.success(data);
    }
}
