package com.qb.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class GatewayHeaderSecurityFilter extends OncePerRequestFilter {

    public static final String USER_ID_ATTR = "userId";
    public static final String USER_ROLE_ATTR = "userRole";

    private final String gatewaySharedKey;

    public GatewayHeaderSecurityFilter(@Value("${app.gateway.shared-key:}") String gatewaySharedKey) {
        this.gatewaySharedKey = gatewaySharedKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String userId = request.getHeader("X-User-Id");
        String role = request.getHeader("X-User-Role");
        String gatewayKey = request.getHeader("X-Gateway-Key");

        if (gatewaySharedKey != null && !gatewaySharedKey.isBlank()) {
            if ((userId != null && !userId.isBlank()) || (role != null && !role.isBlank())) {
                if (!gatewaySharedKey.equals(gatewayKey)) {
                    response.sendError(HttpStatus.FORBIDDEN.value(), "Untrusted identity headers");
                    return;
                }
            }
        } else {
            if (userId == null) userId = "dev-user";
            if (role == null) role = "ADMIN";
        }

        if (userId != null && role != null) {
            String roleWithPrefix = role.startsWith("ROLE_") ? role : "ROLE_" + role;
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority(roleWithPrefix))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
