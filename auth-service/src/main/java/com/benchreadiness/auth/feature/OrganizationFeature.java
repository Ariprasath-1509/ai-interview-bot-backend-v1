package com.benchreadiness.auth.feature;

import jakarta.persistence.*;

@Entity
@Table(name = "org_features", schema = "auth_svc",
       uniqueConstraints = @UniqueConstraint(columnNames = {"org_code", "feature_key"}))
public class OrganizationFeature {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "org_code", nullable = false, length = 64)
    private String orgCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_key", nullable = false, length = 32)
    private FeatureKey featureKey;

    @Column(nullable = false)
    private boolean enabled;

    @PrePersist
    void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
    }

    public String getId() { return id; }
    public String getOrgCode() { return orgCode; }
    public void setOrgCode(String orgCode) { this.orgCode = orgCode; }
    public FeatureKey getFeatureKey() { return featureKey; }
    public void setFeatureKey(FeatureKey featureKey) { this.featureKey = featureKey; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
