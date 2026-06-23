package com.benchreadiness.interview.config;

import com.benchreadiness.interview.branch.BranchSegregation;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.branch-segregation")
public class BranchSegregationProperties {

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
