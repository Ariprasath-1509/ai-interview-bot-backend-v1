package com.benchreadiness.ops.feature;

import com.benchreadiness.ops.org.OrgContext;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

@Aspect
@Component
public class FeatureGateAspect {

    private final OrgFeatureGateService featureGateService;

    public FeatureGateAspect(OrgFeatureGateService featureGateService) {
        this.featureGateService = featureGateService;
    }

    @Around("@annotation(requiresFeature)")
    public Object gate(ProceedingJoinPoint pjp, RequiresFeature requiresFeature) throws Throwable {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String role = null;
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            role = request.getHeader("X-User-Role");
        }
        String org = OrgContext.get();
        if (!"SUPER_ADMIN".equals(role) && !featureGateService.isEnabled(org, requiresFeature.value())) {
            return ResponseEntity.status(403).body(Map.of(
                    "ok", false,
                    "error", "Feature not enabled for this organization: " + requiresFeature.value()));
        }
        return pjp.proceed();
    }
}
