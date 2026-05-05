package com.benchreadiness.interview.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@Component
public class FeignAuthInterceptor implements RequestInterceptor {

    private static final ThreadLocal<Map<String, String>> HEADERS = new ThreadLocal<>();

    public static void setHeaders(Map<String, String> headers) {
        HEADERS.set(headers);
    }

    public static void clear() {
        HEADERS.remove();
    }

    public static Map<String, String> captureFromRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return null;
        HttpServletRequest request = attributes.getRequest();
        Map<String, String> headers = new HashMap<>();
        String auth = request.getHeader("Authorization");
        String userId = request.getHeader("X-User-Id");
        String userRole = request.getHeader("X-User-Role");
        String userEmail = request.getHeader("X-User-Email");
        if (auth != null) headers.put("Authorization", auth);
        if (userId != null) headers.put("X-User-Id", userId);
        if (userRole != null) headers.put("X-User-Role", userRole);
        if (userEmail != null) headers.put("X-User-Email", userEmail);
        return headers;
    }

    @Override
    public void apply(RequestTemplate template) {
        // First try ThreadLocal (works in async threads)
        Map<String, String> threadLocalHeaders = HEADERS.get();
        if (threadLocalHeaders != null) {
            threadLocalHeaders.forEach(template::header);
            return;
        }
        // Fallback to RequestContextHolder (works in servlet threads)
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String auth = request.getHeader("Authorization");
            String userId = request.getHeader("X-User-Id");
            String userRole = request.getHeader("X-User-Role");
            String userEmail = request.getHeader("X-User-Email");
            if (auth != null) template.header("Authorization", auth);
            if (userId != null) template.header("X-User-Id", userId);
            if (userRole != null) template.header("X-User-Role", userRole);
            if (userEmail != null) template.header("X-User-Email", userEmail);
        }
    }
}