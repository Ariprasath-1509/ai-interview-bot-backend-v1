package com.benchreadiness.auth.feature;

import com.benchreadiness.auth.repository.OrganizationFeatureRepository;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

/** A feature with no stored row for an org is enabled by default (backward-compatible rollout). */
@Service
public class OrganizationFeatureService {

    private final OrganizationFeatureRepository repository;

    public OrganizationFeatureService(OrganizationFeatureRepository repository) {
        this.repository = repository;
    }

    public Map<FeatureKey, Boolean> getFeatureStates(String orgCode) {
        Map<FeatureKey, Boolean> states = new EnumMap<>(FeatureKey.class);
        for (FeatureKey key : FeatureKey.values()) {
            states.put(key, true);
        }
        for (OrganizationFeature row : repository.findByOrgCode(orgCode)) {
            states.put(row.getFeatureKey(), row.isEnabled());
        }
        return states;
    }

    public boolean isEnabled(String orgCode, FeatureKey featureKey) {
        if (orgCode == null) return true;
        return repository.findByOrgCodeAndFeatureKey(orgCode, featureKey)
                .map(OrganizationFeature::isEnabled)
                .orElse(true);
    }

    public void setFeatures(String orgCode, Map<FeatureKey, Boolean> updates) {
        updates.forEach((key, enabled) -> {
            OrganizationFeature row = repository.findByOrgCodeAndFeatureKey(orgCode, key)
                    .orElseGet(() -> {
                        OrganizationFeature f = new OrganizationFeature();
                        f.setOrgCode(orgCode);
                        f.setFeatureKey(key);
                        return f;
                    });
            row.setEnabled(enabled);
            repository.save(row);
        });
    }
}
