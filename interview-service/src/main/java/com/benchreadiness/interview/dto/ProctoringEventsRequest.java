package com.benchreadiness.interview.dto;

import java.util.List;
import java.util.Map;

public class ProctoringEventsRequest {
    private List<Map<String, Object>> events;
    private Map<String, Integer> strikes;
    private String status;
    private Integer integrityScore;
    private Map<String, Object> summary;

    public ProctoringEventsRequest() {}

    public List<Map<String, Object>> getEvents() { return events; }
    public void setEvents(List<Map<String, Object>> events) { this.events = events; }
    public Map<String, Integer> getStrikes() { return strikes; }
    public void setStrikes(Map<String, Integer> strikes) { this.strikes = strikes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getIntegrityScore() { return integrityScore; }
    public void setIntegrityScore(Integer integrityScore) { this.integrityScore = integrityScore; }
    public Map<String, Object> getSummary() { return summary; }
    public void setSummary(Map<String, Object> summary) { this.summary = summary; }
}
