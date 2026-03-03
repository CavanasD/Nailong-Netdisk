package com.nailong.netdisk.aspect;

import com.nailong.netdisk.common.Result;
import com.nailong.netdisk.common.annotation.RequirePermission;
import com.nailong.netdisk.common.annotation.RequireRole;
import com.nailong.netdisk.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

@Aspect
@Component
public class RbacAspect {

    @Value("${security.vuln-mode:false}")
    private boolean vulnMode;

    @Autowired
    private UserService userService;

    @Around("@annotation(com.nailong.netdisk.common.annotation.RequireRole)")
    public Object checkRole(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String token = request.getHeader("token");

        if (token == null || token.isEmpty()) {
            return Result.error("未登录");
        }

        String userIdStr = userService.getUserIdByToken(token);
        if (userIdStr == null) {
            return Result.error("登录已过期");
        }
        Long userId = Long.valueOf(userIdStr);

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequireRole requireRole = method.getAnnotation(RequireRole.class);

        String[] requiredRoles = requireRole.value();
        boolean authorized = false;

        if (vulnMode) {
            String debugRole = request.getParameter("debugRole");
            if (debugRole != null) {
                for (String role : requiredRoles) {
                    if (role.equalsIgnoreCase(debugRole)) {
                        authorized = true;
                        break;
                    }
                }
            }
        }

        for (String role : requiredRoles) {
            if (userService.hasRole(userId, role)) {
                authorized = true;
                break;
            }
        }

        // Check for Super Admin bypass implicitly if needed, or rely on hasRole logic
        if (!authorized) {
            // Optional: implicit SUPER_ADMIN check if not explicitly asked
            if (userService.hasRole(userId, "SUPER_ADMIN")) {
                authorized = true;
            }
        }

        if (!authorized) {
            return Result.error("权限不足");
        }

        return joinPoint.proceed();
    }

    @Around("@annotation(com.nailong.netdisk.common.annotation.RequirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String token = request.getHeader("token");

        if (token == null || token.isEmpty()) {
            return Result.error("未登录");
        }

        String userIdStr = userService.getUserIdByToken(token);
        if (userIdStr == null) {
            return Result.error("登录已过期");
        }
        Long userId = Long.valueOf(userIdStr);

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequirePermission requirePermission = method.getAnnotation(RequirePermission.class);

        String[] requiredPermissions = requirePermission.value();
        boolean authorized = false;

        if (vulnMode) {
            String debugPerm = request.getParameter("debugPerm");
            if (debugPerm != null) {
                for (String permission : requiredPermissions) {
                    if (permission.equalsIgnoreCase(debugPerm)) {
                        authorized = true;
                        break;
                    }
                }
            }
        }

        for (String permission : requiredPermissions) {
            if (userService.hasPermission(userId, permission)) {
                authorized = true;
                break;
            }
        }

        if (!authorized) {
            return Result.error("权限不足");
        }

        return joinPoint.proceed();
    }
}

