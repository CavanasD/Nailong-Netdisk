package com.nailong.netdisk.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCachingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest) {
            HttpServletRequest req = (HttpServletRequest) request;
            String contentType = request.getContentType();
            // 只缓存 JSON 请求
            if (contentType != null && contentType.contains("application/json")) {
                // 使用自定义的 Wrapper，不仅缓存，还允许重复读取
                CachedBodyHttpServletRequest cachedBodyRequest = new CachedBodyHttpServletRequest(req);
                chain.doFilter(cachedBodyRequest, response);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
