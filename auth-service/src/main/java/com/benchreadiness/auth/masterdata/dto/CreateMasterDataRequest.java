package com.benchreadiness.auth.masterdata.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public class CreateMasterDataRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String label;

    private int displayOrder;
    private boolean active = true;
    private Map<String, Object> metadata;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
