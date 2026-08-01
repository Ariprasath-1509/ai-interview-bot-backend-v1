package com.benchreadiness.review.feature;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Rejects with 403 when the caller's org has this feature disabled. SUPER_ADMIN always bypasses. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresFeature {
    FeatureKey value();
}
