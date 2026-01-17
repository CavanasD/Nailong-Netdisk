package com.nailong.netdisk.config;

import com.nailong.netdisk.service.UserService;
import com.nailong.netdisk.waf.NailongDefenderInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private UserService userService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new NailongDefenderInterceptor(userService))
                .addPathPatterns("/**");
    }
}
