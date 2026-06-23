package com.benchreadiness.ops.compliance.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component("complianceMdcFilter")
public class MdcFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        try {
            String traceId = httpRequest.getHeader("X-Trace-Id");
            String userId = httpRequest.getHeader("X-User-Id");
            String userRole = httpRequest.getHeader("X-User-Role");
            String userEmail = httpRequest.getHeader("X-User-Email");

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
