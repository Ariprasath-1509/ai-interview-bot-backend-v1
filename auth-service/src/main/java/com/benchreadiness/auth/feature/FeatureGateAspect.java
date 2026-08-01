package com.benchreadiness.auth.feature;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/** Enforces {@link RequiresFeature} by reading the gateway-trusted X-User-Role/X-User-Org headers. */
@Aspect
@Component
public class FeatureGateAspect {

    private final OrganizationFeatureService featureService;

    public FeatureGateAspect(OrganizationFeatureService featureService) {
        this.featureService = featureService;
    }

    @Around("@annotation(requiresFeature)")
    public Object gate(ProceedingJoinPoint pjp, RequiresFeature requiresFeature) throws Throwable {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            String role = request.getHeader("X-User-Role");
            String org = request.getHeader("X-User-Org");
            if (!"SUPER_ADMIN".equals(role) && !featureService.isEnabled(org, requiresFeature.value())) {
                return ResponseEntity.status(403).body(Map.of(
                        "ok", false,
                        "error", "Feature not enabled for this organization: " + requiresFeature.value()));
            }
        }
        return pjp.proceed();
    }
}
