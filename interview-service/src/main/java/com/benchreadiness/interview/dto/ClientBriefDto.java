package com.benchreadiness.interview.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ClientBriefDto {

    private BriefHeader header = new BriefHeader();
    private List<SkillSummary> mustHaveSkills = new ArrayList<>();
    private List<SkillSummary> goodToHaveSkills = new ArrayList<>();
    private List<QuestionAsked> questionsAsked = new ArrayList<>();
    private String overallFeedback = "";
    private String generationWarning = "";
    private List<SkillAssessment> skillAssessments = new ArrayList<>();
    private String source;
    private boolean saved;
    private String lastEditedByUserId;
    private String lastEditedByName;
    private Instant lastEditedAt;

    public static class BriefHeader {
        private String candidateName;
        private String candidateEmail;
        private String positionTitle;
        private String clientName;
        private String roundName;
        private String seniorityBand;
        private String interviewDate;
        private Integer totalMinutes;
        private InterviewerInfo reviewer = new InterviewerInfo();
        private String playbackUrl;
        private String resumeUrl;

        public String getCandidateName() { return candidateName; }
        public void setCandidateName(String candidateName) { this.candidateName = candidateName; }
        public String getCandidateEmail() { return candidateEmail; }
        public void setCandidateEmail(String candidateEmail) { this.candidateEmail = candidateEmail; }
        public String getPositionTitle() { return positionTitle; }
        public void setPositionTitle(String positionTitle) { this.positionTitle = positionTitle; }
        public String getClientName() { return clientName; }
        public void setClientName(String clientName) { this.clientName = clientName; }
        public String getRoundName() { return roundName; }
        public void setRoundName(String roundName) { this.roundName = roundName; }
        public String getSeniorityBand() { return seniorityBand; }
        public void setSeniorityBand(String seniorityBand) { this.seniorityBand = seniorityBand; }
        public String getInterviewDate() { return interviewDate; }
        public void setInterviewDate(String interviewDate) { this.interviewDate = interviewDate; }
        public Integer getTotalMinutes() { return totalMinutes; }
        public void setTotalMinutes(Integer totalMinutes) { this.totalMinutes = totalMinutes; }
        public InterviewerInfo getReviewer() { return reviewer; }
        public void setReviewer(InterviewerInfo reviewer) { this.reviewer = reviewer; }
        public String getPlaybackUrl() { return playbackUrl; }
        public void setPlaybackUrl(String playbackUrl) { this.playbackUrl = playbackUrl; }
        public String getResumeUrl() { return resumeUrl; }
        public void setResumeUrl(String resumeUrl) { this.resumeUrl = resumeUrl; }
    }

    public static class InterviewerInfo {
        private String name;
        private String yearsExperience;
        private String company;
        private List<String> verifiedSkills = new ArrayList<>();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getYearsExperience() { return yearsExperience; }
        public void setYearsExperience(String yearsExperience) { this.yearsExperience = yearsExperience; }
        public String getCompany() { return company; }
        public void setCompany(String company) { this.company = company; }
        public List<String> getVerifiedSkills() { return verifiedSkills; }
        public void setVerifiedSkills(List<String> verifiedSkills) { this.verifiedSkills = verifiedSkills; }
    }

    public static class SkillSummary {
        private int rank;
        private String categoryKey;
        private String skill;
        private String subSkill;
        private int score10;
        private String note;

        public int getRank() { return rank; }
        public void setRank(int rank) { this.rank = rank; }
        public String getCategoryKey() { return categoryKey; }
        public void setCategoryKey(String categoryKey) { this.categoryKey = categoryKey; }
        public String getSkill() { return skill; }
        public void setSkill(String skill) { this.skill = skill; }
        public String getSubSkill() { return subSkill; }
        public void setSubSkill(String subSkill) { this.subSkill = subSkill; }
        public int getScore10() { return score10; }
        public void setScore10(int score10) { this.score10 = score10; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }

    public static class SkillMapping {
        private String skill;
        private String subSkill;
        private String categoryKey;

        public String getSkill() { return skill; }
        public void setSkill(String skill) { this.skill = skill; }
        public String getSubSkill() { return subSkill; }
        public void setSubSkill(String subSkill) { this.subSkill = subSkill; }
        public String getCategoryKey() { return categoryKey; }
        public void setCategoryKey(String categoryKey) { this.categoryKey = categoryKey; }
    }

    public static class QuestionAsked {
        private int number;
        private String difficulty;
        private String text;
        private String answer;
        private String type;
        private List<SkillMapping> skillMappings = new ArrayList<>();

        public int getNumber() { return number; }
        public void setNumber(int number) { this.number = number; }
        public String getDifficulty() { return difficulty; }
        public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public String getAnswer() { return answer; }
        public void setAnswer(String answer) { this.answer = answer; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public List<SkillMapping> getSkillMappings() { return skillMappings; }
        public void setSkillMappings(List<SkillMapping> skillMappings) { this.skillMappings = skillMappings; }
    }

    public static class SkillAssessment {
        private int rank;
        private String categoryKey;
        private String skill;
        private String subSkill;
        private int score10;
        private List<String> proficiencyOptions = new ArrayList<>();
        private int selectedProficiencyIndex;
        private List<String> strengths = new ArrayList<>();
        private List<String> areasOfImprovement = new ArrayList<>();

        public int getRank() { return rank; }
        public void setRank(int rank) { this.rank = rank; }
        public String getCategoryKey() { return categoryKey; }
        public void setCategoryKey(String categoryKey) { this.categoryKey = categoryKey; }
        public String getSkill() { return skill; }
        public void setSkill(String skill) { this.skill = skill; }
        public String getSubSkill() { return subSkill; }
        public void setSubSkill(String subSkill) { this.subSkill = subSkill; }
        public int getScore10() { return score10; }
        public void setScore10(int score10) { this.score10 = score10; }
        public List<String> getProficiencyOptions() { return proficiencyOptions; }
        public void setProficiencyOptions(List<String> proficiencyOptions) { this.proficiencyOptions = proficiencyOptions; }
        public int getSelectedProficiencyIndex() { return selectedProficiencyIndex; }
        public void setSelectedProficiencyIndex(int selectedProficiencyIndex) { this.selectedProficiencyIndex = selectedProficiencyIndex; }
        public List<String> getStrengths() { return strengths; }
        public void setStrengths(List<String> strengths) { this.strengths = strengths; }
        public List<String> getAreasOfImprovement() { return areasOfImprovement; }
        public void setAreasOfImprovement(List<String> areasOfImprovement) { this.areasOfImprovement = areasOfImprovement; }
    }

    public BriefHeader getHeader() { return header; }
    public void setHeader(BriefHeader header) { this.header = header; }
    public List<SkillSummary> getMustHaveSkills() { return mustHaveSkills; }
    public void setMustHaveSkills(List<SkillSummary> mustHaveSkills) { this.mustHaveSkills = mustHaveSkills; }
    public List<SkillSummary> getGoodToHaveSkills() { return goodToHaveSkills; }
    public void setGoodToHaveSkills(List<SkillSummary> goodToHaveSkills) { this.goodToHaveSkills = goodToHaveSkills; }
    public List<QuestionAsked> getQuestionsAsked() { return questionsAsked; }
    public void setQuestionsAsked(List<QuestionAsked> questionsAsked) { this.questionsAsked = questionsAsked; }
    public String getOverallFeedback() { return overallFeedback; }
    public void setOverallFeedback(String overallFeedback) { this.overallFeedback = overallFeedback; }
    public String getGenerationWarning() { return generationWarning; }
    public void setGenerationWarning(String generationWarning) { this.generationWarning = generationWarning; }
    public List<SkillAssessment> getSkillAssessments() { return skillAssessments; }
    public void setSkillAssessments(List<SkillAssessment> skillAssessments) { this.skillAssessments = skillAssessments; }
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

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("header", headerToMap(header));
        map.put("mustHaveSkills", skillSummariesToMaps(mustHaveSkills));
        map.put("goodToHaveSkills", skillSummariesToMaps(goodToHaveSkills));
        map.put("questionsAsked", questionsToMaps(questionsAsked));
        map.put("overallFeedback", overallFeedback);
        map.put("generationWarning", generationWarning);
        map.put("skillAssessments", assessmentsToMaps(skillAssessments));
        map.put("source", source);
        map.put("saved", saved);
        map.put("lastEditedByUserId", lastEditedByUserId);
        map.put("lastEditedByName", lastEditedByName);
        map.put("lastEditedAt", lastEditedAt != null ? lastEditedAt.toString() : null);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static ClientBriefDto fromMap(Map<String, Object> map) {
        ClientBriefDto dto = new ClientBriefDto();
        if (map == null) return dto;

        Object headerObj = map.get("header");
        if (headerObj instanceof Map<?, ?> headerMap) {
            dto.setHeader(parseHeader((Map<String, Object>) headerMap));
        }

        dto.setMustHaveSkills(parseSkillSummaries(map.get("mustHaveSkills")));
        dto.setGoodToHaveSkills(parseSkillSummaries(map.get("goodToHaveSkills")));
        dto.setQuestionsAsked(parseQuestions(map.get("questionsAsked")));
        dto.setOverallFeedback(stringVal(map.get("overallFeedback")));
        dto.setGenerationWarning(stringVal(map.get("generationWarning")));
        dto.setSkillAssessments(parseAssessments(map.get("skillAssessments")));
        dto.setSource(stringVal(map.get("source")));
        if (map.get("saved") instanceof Boolean b) dto.setSaved(b);
        dto.setLastEditedByUserId(stringVal(map.get("lastEditedByUserId")));
        dto.setLastEditedByName(stringVal(map.get("lastEditedByName")));
        String editedAt = stringVal(map.get("lastEditedAt"));
        if (!editedAt.isBlank()) {
            try { dto.setLastEditedAt(Instant.parse(editedAt)); } catch (Exception ignored) {}
        }
        return dto;
    }

    @SuppressWarnings("unchecked")
    private static BriefHeader parseHeader(Map<String, Object> map) {
        BriefHeader header = new BriefHeader();
        header.setCandidateName(stringVal(map.get("candidateName")));
        header.setCandidateEmail(stringVal(map.get("candidateEmail")));
        header.setPositionTitle(stringVal(map.get("positionTitle")));
        header.setClientName(stringVal(map.get("clientName")));
        header.setRoundName(stringVal(map.get("roundName")));
        header.setSeniorityBand(stringVal(map.get("seniorityBand")));
        header.setInterviewDate(stringVal(map.get("interviewDate")));
        if (map.get("totalMinutes") != null) {
            try { header.setTotalMinutes(Integer.parseInt(map.get("totalMinutes").toString())); } catch (Exception ignored) {}
        }
        header.setPlaybackUrl(stringVal(map.get("playbackUrl")));
        header.setResumeUrl(stringVal(map.get("resumeUrl")));
        Object reviewerObj = map.get("reviewer");
        if (reviewerObj instanceof Map<?, ?> reviewerMap) {
            InterviewerInfo reviewer = new InterviewerInfo();
            reviewer.setName(stringVal(reviewerMap.get("name")));
            reviewer.setYearsExperience(stringVal(reviewerMap.get("yearsExperience")));
            reviewer.setCompany(stringVal(reviewerMap.get("company")));
            reviewer.setVerifiedSkills(stringList(reviewerMap.get("verifiedSkills")));
            header.setReviewer(reviewer);
        }
        return header;
    }

    @SuppressWarnings("unchecked")
    private static List<SkillSummary> parseSkillSummaries(Object value) {
        List<SkillSummary> out = new ArrayList<>();
        if (!(value instanceof List<?> list)) return out;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            SkillSummary s = new SkillSummary();
            try { s.setRank(Integer.parseInt(String.valueOf(map.get("rank")))); } catch (Exception ignored) {}
            s.setCategoryKey(stringVal(map.get("categoryKey")));
            s.setSkill(stringVal(map.get("skill")));
            s.setSubSkill(stringVal(map.get("subSkill")));
            try { s.setScore10(Integer.parseInt(String.valueOf(map.get("score10")))); } catch (Exception ignored) {}
            s.setNote(stringVal(map.get("note")));
            out.add(s);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<QuestionAsked> parseQuestions(Object value) {
        List<QuestionAsked> out = new ArrayList<>();
        if (!(value instanceof List<?> list)) return out;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            QuestionAsked q = new QuestionAsked();
            try { q.setNumber(Integer.parseInt(String.valueOf(map.get("number")))); } catch (Exception ignored) {}
            q.setDifficulty(stringVal(map.get("difficulty")));
            q.setText(stringVal(map.get("text")));
            q.setAnswer(stringVal(map.get("answer")));
            q.setType(stringVal(map.get("type")));
            q.setSkillMappings(parseSkillMappings(map.get("skillMappings")));
            out.add(q);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<SkillMapping> parseSkillMappings(Object value) {
        List<SkillMapping> out = new ArrayList<>();
        if (!(value instanceof List<?> list)) return out;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            SkillMapping m = new SkillMapping();
            m.setSkill(stringVal(map.get("skill")));
            m.setSubSkill(stringVal(map.get("subSkill")));
            m.setCategoryKey(stringVal(map.get("categoryKey")));
            out.add(m);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<SkillAssessment> parseAssessments(Object value) {
        List<SkillAssessment> out = new ArrayList<>();
        if (!(value instanceof List<?> list)) return out;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            SkillAssessment a = new SkillAssessment();
            try { a.setRank(Integer.parseInt(String.valueOf(map.get("rank")))); } catch (Exception ignored) {}
            a.setCategoryKey(stringVal(map.get("categoryKey")));
            a.setSkill(stringVal(map.get("skill")));
            a.setSubSkill(stringVal(map.get("subSkill")));
            try { a.setScore10(Integer.parseInt(String.valueOf(map.get("score10")))); } catch (Exception ignored) {}
            a.setProficiencyOptions(stringList(map.get("proficiencyOptions")));
            try { a.setSelectedProficiencyIndex(Integer.parseInt(String.valueOf(map.get("selectedProficiencyIndex")))); }
            catch (Exception ignored) {}
            a.setStrengths(stringList(map.get("strengths")));
            a.setAreasOfImprovement(stringList(map.get("areasOfImprovement")));
            out.add(a);
        }
        return out;
    }

    private static Map<String, Object> headerToMap(BriefHeader header) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (header == null) return map;
        map.put("candidateName", header.getCandidateName());
        map.put("candidateEmail", header.getCandidateEmail());
        map.put("positionTitle", header.getPositionTitle());
        map.put("clientName", header.getClientName());
        map.put("roundName", header.getRoundName());
        map.put("seniorityBand", header.getSeniorityBand());
        map.put("interviewDate", header.getInterviewDate());
        map.put("totalMinutes", header.getTotalMinutes());
        map.put("playbackUrl", header.getPlaybackUrl());
        map.put("resumeUrl", header.getResumeUrl());
        InterviewerInfo reviewer = header.getReviewer();
        Map<String, Object> reviewerMap = new LinkedHashMap<>();
        if (reviewer != null) {
            reviewerMap.put("name", reviewer.getName());
            reviewerMap.put("yearsExperience", reviewer.getYearsExperience());
            reviewerMap.put("company", reviewer.getCompany());
            reviewerMap.put("verifiedSkills", reviewer.getVerifiedSkills());
        }
        map.put("reviewer", reviewerMap);
        return map;
    }

    private static List<Map<String, Object>> skillSummariesToMaps(List<SkillSummary> items) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (items == null) return out;
        for (SkillSummary s : items) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("rank", s.getRank());
            map.put("categoryKey", s.getCategoryKey());
            map.put("skill", s.getSkill());
            map.put("subSkill", s.getSubSkill());
            map.put("score10", s.getScore10());
            map.put("note", s.getNote());
            out.add(map);
        }
        return out;
    }

    private static List<Map<String, Object>> questionsToMaps(List<QuestionAsked> items) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (items == null) return out;
        for (QuestionAsked q : items) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("number", q.getNumber());
            map.put("difficulty", q.getDifficulty());
            map.put("text", q.getText());
            map.put("answer", q.getAnswer());
            map.put("type", q.getType());
            List<Map<String, Object>> mappings = new ArrayList<>();
            for (SkillMapping m : q.getSkillMappings()) {
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("skill", m.getSkill());
                mm.put("subSkill", m.getSubSkill());
                mm.put("categoryKey", m.getCategoryKey());
                mappings.add(mm);
            }
            map.put("skillMappings", mappings);
            out.add(map);
        }
        return out;
    }

    private static List<Map<String, Object>> assessmentsToMaps(List<SkillAssessment> items) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (items == null) return out;
        for (SkillAssessment a : items) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("rank", a.getRank());
            map.put("categoryKey", a.getCategoryKey());
            map.put("skill", a.getSkill());
            map.put("subSkill", a.getSubSkill());
            map.put("score10", a.getScore10());
            map.put("proficiencyOptions", a.getProficiencyOptions());
            map.put("selectedProficiencyIndex", a.getSelectedProficiencyIndex());
            map.put("strengths", a.getStrengths());
            map.put("areasOfImprovement", a.getAreasOfImprovement());
            out.add(map);
        }
        return out;
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
                if (item != null && !item.toString().isBlank()) out.add(item.toString());
            }
            return out;
        }
        if (!value.toString().isBlank()) return new ArrayList<>(List.of(value.toString()));
        return new ArrayList<>();
    }
}
