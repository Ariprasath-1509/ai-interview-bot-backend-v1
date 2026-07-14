package com.benchreadiness.interview.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class CandidateReviewSummary {
    private CandidateInfo candidateInfo;
    private OverviewStats overviewStats;
    private List<InterviewDetail> interviews;

    public static class CandidateInfo {
        private String name;
        private String email;
        private String skillSet;
        private Double yoeActual;
        private Double yoePortrayed;
        private String batch;
        private String source;

        public CandidateInfo(String name, String email, String skillSet, Double yoeActual, Double yoePortrayed, String batch, String source) {
            this.name = name;
            this.email = email;
            this.skillSet = skillSet;
            this.yoeActual = yoeActual;
            this.yoePortrayed = yoePortrayed;
            this.batch = batch;
            this.source = source;
        }

        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getSkillSet() { return skillSet; }
        public Double getYoeActual() { return yoeActual; }
        public Double getYoePortrayed() { return yoePortrayed; }
        public String getBatch() { return batch; }
        public String getSource() { return source; }
    }

    public static class OverviewStats {
        private int totalInterviews;
        private int readyCount;
        private double successRate;

        public OverviewStats(int totalInterviews, int readyCount, double successRate) {
            this.totalInterviews = totalInterviews;
            this.readyCount = readyCount;
            this.successRate = successRate;
        }

        public int getTotalInterviews() { return totalInterviews; }
        public int getReadyCount() { return readyCount; }
        public double getSuccessRate() { return successRate; }
    }

    public static class InterviewDetail {
        private String interviewId;
        private Instant interviewDate;
        private String jdTitle;
        private String interviewMode;
        private String verdict;
        private String summary;
        private List<CategoryScore> categoryScores;
        private List<ProCon> prosAndCons;
        private ResumeConsistency resumeConsistency;
        private BehavioralSignals behavioralSignals;
        private List<RoadmapItem> roadmap;

        public String getInterviewId() { return interviewId; }
        public void setInterviewId(String interviewId) { this.interviewId = interviewId; }
        public Instant getInterviewDate() { return interviewDate; }
        public void setInterviewDate(Instant interviewDate) { this.interviewDate = interviewDate; }
        public String getJdTitle() { return jdTitle; }
        public void setJdTitle(String jdTitle) { this.jdTitle = jdTitle; }
        public String getInterviewMode() { return interviewMode; }
        public void setInterviewMode(String interviewMode) { this.interviewMode = interviewMode; }
        public String getVerdict() { return verdict; }
        public void setVerdict(String verdict) { this.verdict = verdict; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public List<CategoryScore> getCategoryScores() { return categoryScores; }
        public void setCategoryScores(List<CategoryScore> categoryScores) { this.categoryScores = categoryScores; }
        public List<ProCon> getProsAndCons() { return prosAndCons; }
        public void setProsAndCons(List<ProCon> prosAndCons) { this.prosAndCons = prosAndCons; }
        public ResumeConsistency getResumeConsistency() { return resumeConsistency; }
        public void setResumeConsistency(ResumeConsistency resumeConsistency) { this.resumeConsistency = resumeConsistency; }
        public BehavioralSignals getBehavioralSignals() { return behavioralSignals; }
        public void setBehavioralSignals(BehavioralSignals behavioralSignals) { this.behavioralSignals = behavioralSignals; }
        public List<RoadmapItem> getRoadmap() { return roadmap; }
        public void setRoadmap(List<RoadmapItem> roadmap) { this.roadmap = roadmap; }
    }

    public static class CategoryScore {
        private String dimension;
        private int value;
        private String strengths;
        private String weaknesses;
        private String evidence;
        private String gap;
        private String confidence;

        public String getDimension() { return dimension; }
        public void setDimension(String dimension) { this.dimension = dimension; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
        public String getStrengths() { return strengths; }
        public void setStrengths(String strengths) { this.strengths = strengths; }
        public String getWeaknesses() { return weaknesses; }
        public void setWeaknesses(String weaknesses) { this.weaknesses = weaknesses; }
        public String getEvidence() { return evidence; }
        public void setEvidence(String evidence) { this.evidence = evidence; }
        public String getGap() { return gap; }
        public void setGap(String gap) { this.gap = gap; }
        public String getConfidence() { return confidence; }
        public void setConfidence(String confidence) { this.confidence = confidence; }
    }

    public static class ProCon {
        private String category;
        private List<String> pros;
        private List<String> cons;

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public List<String> getPros() { return pros; }
        public void setPros(List<String> pros) { this.pros = pros; }
        public List<String> getCons() { return cons; }
        public void setCons(List<String> cons) { this.cons = cons; }
    }

    public static class ResumeConsistency {
        private List<String> claimed;
        private List<String> demonstrated;
        private List<String> notDemonstrated;
        private int consistencyScore;
        private List<String> flags;

        public List<String> getClaimed() { return claimed; }
        public void setClaimed(List<String> claimed) { this.claimed = claimed; }
        public List<String> getDemonstrated() { return demonstrated; }
        public void setDemonstrated(List<String> demonstrated) { this.demonstrated = demonstrated; }
        public List<String> getNotDemonstrated() { return notDemonstrated; }
        public void setNotDemonstrated(List<String> notDemonstrated) { this.notDemonstrated = notDemonstrated; }
        public int getConsistencyScore() { return consistencyScore; }
        public void setConsistencyScore(int consistencyScore) { this.consistencyScore = consistencyScore; }
        public List<String> getFlags() { return flags; }
        public void setFlags(List<String> flags) { this.flags = flags; }
    }

    public static class BehavioralSignals {
        private String ownershipLevel;
        private String learningAgility;
        private String communicationStructure;
        private String confidenceCalibration;
        private String summary;

        public String getOwnershipLevel() { return ownershipLevel; }
        public void setOwnershipLevel(String ownershipLevel) { this.ownershipLevel = ownershipLevel; }
        public String getLearningAgility() { return learningAgility; }
        public void setLearningAgility(String learningAgility) { this.learningAgility = learningAgility; }
        public String getCommunicationStructure() { return communicationStructure; }
        public void setCommunicationStructure(String communicationStructure) { this.communicationStructure = communicationStructure; }
        public String getConfidenceCalibration() { return confidenceCalibration; }
        public void setConfidenceCalibration(String confidenceCalibration) { this.confidenceCalibration = confidenceCalibration; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
    }

    public static class RoadmapItem {
        private String day;
        private String category;
        private String gap;
        private String focus;
        private String whyItMatters;
        private String resource;
        private String exercise;
        private int estimatedHours;

        public String getDay() { return day; }
        public void setDay(String day) { this.day = day; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getGap() { return gap; }
        public void setGap(String gap) { this.gap = gap; }
        public String getFocus() { return focus; }
        public void setFocus(String focus) { this.focus = focus; }
        public String getWhyItMatters() { return whyItMatters; }
        public void setWhyItMatters(String whyItMatters) { this.whyItMatters = whyItMatters; }
        public String getResource() { return resource; }
        public void setResource(String resource) { this.resource = resource; }
        public String getExercise() { return exercise; }
        public void setExercise(String exercise) { this.exercise = exercise; }
        public int getEstimatedHours() { return estimatedHours; }
        public void setEstimatedHours(int estimatedHours) { this.estimatedHours = estimatedHours; }
    }

    public CandidateInfo getCandidateInfo() { return candidateInfo; }
    public void setCandidateInfo(CandidateInfo candidateInfo) { this.candidateInfo = candidateInfo; }
    public OverviewStats getOverviewStats() { return overviewStats; }
    public void setOverviewStats(OverviewStats overviewStats) { this.overviewStats = overviewStats; }
    public List<InterviewDetail> getInterviews() { return interviews; }
    public void setInterviews(List<InterviewDetail> interviews) { this.interviews = interviews; }
}
