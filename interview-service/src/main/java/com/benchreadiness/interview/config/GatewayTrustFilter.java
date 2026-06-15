package com.benchreadiness.interview.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class GatewayTrustFilter extends OncePerRequestFilter {

    private final String gatewaySharedKey;

    public GatewayTrustFilter(@Value("${app.gateway.shared-key:}") String gatewaySharedKey) {
        this.gatewaySharedKey = gatewaySharedKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (gatewaySharedKey == null || gatewaySharedKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = request.getHeader("X-User-Id");
        String userRole = request.getHeader("X-User-Role");
        if ((userId != null && !userId.isBlank()) || (userRole != null && !userRole.isBlank())) {
            String gatewayKey = request.getHeader("X-Gateway-Key");
            if (!gatewaySharedKey.equals(gatewayKey)) {
                response.sendError(HttpStatus.FORBIDDEN.value(), "Untrusted identity headers");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
