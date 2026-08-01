package com.benchreadiness.interview.feature;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rejects the request with 403 when the caller's org has this feature disabled (per auth-service's
 * org_features table). SUPER_ADMIN is cross-org and always bypasses. Enforced by
 * {@link FeatureGateAspect}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresFeature {
    FeatureKey value();
}
