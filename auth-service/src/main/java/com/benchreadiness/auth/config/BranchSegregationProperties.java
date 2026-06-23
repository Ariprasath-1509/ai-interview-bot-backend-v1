package com.benchreadiness.auth.config;

import com.benchreadiness.auth.branch.BranchSegregation;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.branch-segregation")
public class BranchSegregationProperties {

    /** When false, branch filters are bypassed (legacy behavior). */
    private boolean enabled;

    @PostConstruct
    void apply() {
        BranchSegregation.setEnabled(enabled);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
