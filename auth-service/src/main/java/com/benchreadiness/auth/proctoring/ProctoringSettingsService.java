package com.benchreadiness.auth.proctoring;

import com.benchreadiness.auth.exception.ResourceNotFoundException;
import com.benchreadiness.auth.masterdata.MasterDataCategory;
import com.benchreadiness.auth.masterdata.MasterDataEntry;
import com.benchreadiness.auth.masterdata.MasterDataEntryRepository;
import com.benchreadiness.auth.masterdata.MasterDataService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ProctoringSettingsService {

    static final List<String> SOURCES = List.of("BENCH", "B2B", "MARKET");
    static final String STRICT_LOCKDOWN_KEY = "STRICT_LOCKDOWN";

    private final MasterDataService masterDataService;
    private final MasterDataEntryRepository repository;

    public ProctoringSettingsService(MasterDataService masterDataService,
                                       MasterDataEntryRepository repository) {
        this.masterDataService = masterDataService;
        this.repository = repository;
    }

    public Map<String, Boolean> getSettings() {
        Map<String, Boolean> settings = new LinkedHashMap<>();
        for (String source : SOURCES) {
            settings.put(source, isVideoProctoringEnabled(source));
        }
        settings.put(STRICT_LOCKDOWN_KEY, isStrictLockdownEnabled());
        return settings;
    }

    /** Global switch — immediate termination on tab-switch/blur/fullscreen-exit, plus
     *  best-effort OS key capture (Windows/Meta key, Alt+Tab) during interviews. */
    public boolean isStrictLockdownEnabled() {
        MasterDataEntry entry = repository.findByCategoryAndCodeIgnoreCase(
                MasterDataCategory.SYSTEM_SETTING, STRICT_LOCKDOWN_KEY).orElse(null);
        if (entry == null || !entry.isActive()) {
            return true;
        }
        return readFlag(entry.getMetadata(), "enabled", true);
    }

    public boolean isVideoProctoringEnabled(String candidateSource) {
        if (candidateSource == null || candidateSource.isBlank()) {
            return false;
        }
        String normalized = masterDataService.normalizeCode(candidateSource);
        MasterDataEntry entry = repository.findByCategoryAndCodeIgnoreCase(
                MasterDataCategory.CANDIDATE_SOURCE, normalized).orElse(null);
        if (entry == null || !entry.isActive()) {
            return defaultEnabled(normalized);
        }
        return readEnabledFlag(entry.getMetadata(), defaultEnabled(normalized));
    }

    @Transactional
    public Map<String, Boolean> updateSettings(Map<String, Boolean> updates) {
        if (updates == null || updates.isEmpty()) {
            throw new IllegalArgumentException("At least one source setting is required");
        }

        for (String source : SOURCES) {
            Boolean enabled = updates.get(source);
            if (enabled == null) {
                continue;
            }
            MasterDataEntry entry = repository.findByCategoryAndCodeIgnoreCase(
                            MasterDataCategory.CANDIDATE_SOURCE, source)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Candidate source not found: " + source));

            Map<String, Object> metadata = entry.getMetadata() != null
                    ? new LinkedHashMap<>(entry.getMetadata())
                    : new LinkedHashMap<>();
            metadata.put("videoProctoringEnabled", enabled);
            entry.setMetadata(metadata);
            repository.save(entry);
        }

        Boolean strictLockdown = updates.get(STRICT_LOCKDOWN_KEY);
        if (strictLockdown != null) {
            MasterDataEntry entry = repository.findByCategoryAndCodeIgnoreCase(
                            MasterDataCategory.SYSTEM_SETTING, STRICT_LOCKDOWN_KEY)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "System setting not found: " + STRICT_LOCKDOWN_KEY));

            Map<String, Object> metadata = entry.getMetadata() != null
                    ? new LinkedHashMap<>(entry.getMetadata())
                    : new LinkedHashMap<>();
            metadata.put("enabled", strictLockdown);
            entry.setMetadata(metadata);
            repository.save(entry);
        }

        masterDataService.refreshCache();
        return getSettings();
    }

    private static boolean defaultEnabled(String normalizedSource) {
        return "MARKET".equals(normalizedSource);
    }

    private static boolean readEnabledFlag(Map<String, Object> metadata, boolean defaultValue) {
        return readFlag(metadata, "videoProctoringEnabled", defaultValue);
    }

    private static boolean readFlag(Map<String, Object> metadata, String key, boolean defaultValue) {
        if (metadata == null) {
            return defaultValue;
        }
        Object value = metadata.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return defaultValue;
    }
}
