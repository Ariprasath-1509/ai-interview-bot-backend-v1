package com.benchreadiness.interview.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ClientBriefDto {
    private String executiveSummary;
    private List<String> keyStrengths = new ArrayList<>();
    private List<String> areasToNote = new ArrayList<>();
    private TechnicalFit technicalFit = new TechnicalFit();
    private InterviewPerformance interviewPerformance = new InterviewPerformance();
    private String recommendation;
    private String recommendedFor;
    private String suggestedNextStep;
    private String source;
    private boolean saved;
    private String lastEditedByUserId;
    private String lastEditedByName;
    private Instant lastEditedAt;

    public static class TechnicalFit {
        private String overall;
        private List<String> highlights = new ArrayList<>();
        private List<String> gaps = new ArrayList<>();

        public String getOverall() { return overall; }
        public void setOverall(String overall) { this.overall = overall; }
        public List<String> getHighlights() { return highlights; }
        public void setHighlights(List<String> highlights) { this.highlights = highlights; }
        public List<String> getGaps() { return gaps; }
        public void setGaps(List<String> gaps) { this.gaps = gaps; }
    }

    public static class InterviewPerformance {
        private String communication;
        private String problemSolving;
        private String overallRating;

        public String getCommunication() { return communication; }
        public void setCommunication(String communication) { this.communication = communication; }
        public String getProblemSolving() { return problemSolving; }
        public void setProblemSolving(String problemSolving) { this.problemSolving = problemSolving; }
        public String getOverallRating() { return overallRating; }
        public void setOverallRating(String overallRating) { this.overallRating = overallRating; }
    }

    public String getExecutiveSummary() { return executiveSummary; }
    public void setExecutiveSummary(String executiveSummary) { this.executiveSummary = executiveSummary; }
    public List<String> getKeyStrengths() { return keyStrengths; }
    public void setKeyStrengths(List<String> keyStrengths) { this.keyStrengths = keyStrengths; }
    public List<String> getAreasToNote() { return areasToNote; }
    public void setAreasToNote(List<String> areasToNote) { this.areasToNote = areasToNote; }
    public TechnicalFit getTechnicalFit() { return technicalFit; }
    public void setTechnicalFit(TechnicalFit technicalFit) { this.technicalFit = technicalFit; }
    public InterviewPerformance getInterviewPerformance() { return interviewPerformance; }
    public void setInterviewPerformance(InterviewPerformance interviewPerformance) {
        this.interviewPerformance = interviewPerformance;
    }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    public String getRecommendedFor() { return recommendedFor; }
    public void setRecommendedFor(String recommendedFor) { this.recommendedFor = recommendedFor; }
    public String getSuggestedNextStep() { return suggestedNextStep; }
    public void setSuggestedNextStep(String suggestedNextStep) { this.suggestedNextStep = suggestedNextStep; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public boolean isSaved() { return saved; }
    public void setSaved(boolean saved) { this.saved = saved; }
    public String getLastEditedByUserId() { return lastEditedByUserId; }
    public void setLastEditedByUserId(String lastEditedByUserId) { this.lastEditedByUserId = lastEditedByUserId; }
    public String getLastEditedByName() { return lastEditedByName; }
    public void setLastEditedByName(String lastEditedByName) { this.lastEditedByName = lastEditedByName; }
    public Instant getLastEditedAt() { return lastEditedAt; }
    public void setLastEditedAt(Instant lastEditedAt) { this.lastEditedAt = lastEditedAt; }

    @SuppressWarnings("unchecked")
    public static ClientBriefDto fromMap(Map<String, Object> map) {
        ClientBriefDto dto = new ClientBriefDto();
        if (map == null) return dto;
        dto.setExecutiveSummary(stringVal(map.get("executiveSummary")));
        dto.setKeyStrengths(stringList(map.get("keyStrengths")));
        dto.setAreasToNote(stringList(map.get("areasToNote")));
        dto.setRecommendation(stringVal(map.get("recommendation")));
        dto.setRecommendedFor(stringVal(map.get("recommendedFor")));
        dto.setSuggestedNextStep(stringVal(map.get("suggestedNextStep")));
        dto.setSource(stringVal(map.get("source")));

        Object fitObj = map.get("technicalFit");
        if (fitObj instanceof Map<?, ?> fit) {
            TechnicalFit tf = new TechnicalFit();
            tf.setOverall(stringVal(fit.get("overall")));
            tf.setHighlights(stringList(fit.get("highlights")));
            tf.setGaps(stringList(fit.get("gaps")));
            dto.setTechnicalFit(tf);
        }

        Object perfObj = map.get("interviewPerformance");
        if (perfObj instanceof Map<?, ?> perf) {
            InterviewPerformance ip = new InterviewPerformance();
            ip.setCommunication(stringVal(perf.get("communication")));
            ip.setProblemSolving(stringVal(perf.get("problemSolving")));
            ip.setOverallRating(stringVal(perf.get("overallRating")));
            dto.setInterviewPerformance(ip);
        }
        return dto;
    }

    private static String stringVal(Object value) {
        return value != null ? value.toString() : "";
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object value) {
        if (value == null) return new ArrayList<>();
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !item.toString().isBlank()) {
                    out.add(item.toString());
                }
            }
            return out;
        }
        if (!value.toString().isBlank()) {
            return new ArrayList<>(List.of(value.toString()));
        }
        return new ArrayList<>();
    }
}
