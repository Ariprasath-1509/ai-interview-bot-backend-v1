package com.benchreadiness.auth.proctoring.dto;

import java.util.Map;

public class UpdateProctoringSettingsRequest {

    private Map<String, Boolean> settings;

    public Map<String, Boolean> getSettings() {
        return settings;
    }

    public void setSettings(Map<String, Boolean> settings) {
        this.settings = settings;
    }
}
