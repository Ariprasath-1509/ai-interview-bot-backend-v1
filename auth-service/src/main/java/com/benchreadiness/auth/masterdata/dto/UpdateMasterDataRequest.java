package com.benchreadiness.auth.masterdata.dto;

import java.util.Map;

public class UpdateMasterDataRequest {

    private String label;
    private Integer displayOrder;
    private Boolean active;
    private Map<String, Object> metadata;

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
