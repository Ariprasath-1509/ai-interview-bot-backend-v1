package com.benchreadiness.auth.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class MdcFilter implements Filter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String USER_EMAIL_HEADER = "X-User-Email";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        
        try {
            String traceId = httpRequest.getHeader(TRACE_ID_HEADER);
            String userId = httpRequest.getHeader(USER_ID_HEADER);
            String userRole = httpRequest.getHeader(USER_ROLE_HEADER);
            String userEmail = httpRequest.getHeader(USER_EMAIL_HEADER);
            
            if (traceId != null) MDC.put("traceId", traceId);
            if (userId != null) MDC.put("userId", userId);
            if (userRole != null) MDC.put("userRole", userRole);
            if (userEmail != null) MDC.put("userEmail", userEmail);
            
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
