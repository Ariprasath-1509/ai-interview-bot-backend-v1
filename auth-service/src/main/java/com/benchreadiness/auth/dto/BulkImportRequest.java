package com.benchreadiness.auth.dto;

import java.util.ArrayList;
import java.util.List;

public class BulkImportRequest {
    private String sessionId;
    private List<CandidateBulkData> candidates;
    private boolean confirmImport;
    private boolean canProceed;
    private List<BulkImportResponse.ValidationError> validationErrors = new ArrayList<>();

    public BulkImportRequest() {}

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public List<CandidateBulkData> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<CandidateBulkData> candidates) {
        this.candidates = candidates;
    }

    public boolean isConfirmImport() {
        return confirmImport;
    }

    public void setConfirmImport(boolean confirmImport) {
        this.confirmImport = confirmImport;
    }

    public boolean isCanProceed() {
        return canProceed;
    }

    public void setCanProceed(boolean canProceed) {
        this.canProceed = canProceed;
    }

    public List<BulkImportResponse.ValidationError> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(List<BulkImportResponse.ValidationError> validationErrors) {
        this.validationErrors = validationErrors != null ? validationErrors : new ArrayList<>();
    }

    public static class CandidateBulkData {
        private int rowNumber;
        private String batch;
        private String batchMentor;
        private String source;
        private String status;
        private String rating;
        private String name;
        private String contactNumber;
        private String officialEmail;
        private String personalEmail;
        private Double yoeActual;
        private Double yoePortrayed;
        private String skillSet;
        private Integer noOfInterviews;
        private String interviewMentorName;
        private String clientName;
        private Integer yop;

        public CandidateBulkData() {}

        public int getRowNumber() {
            return rowNumber;
        }

        public void setRowNumber(int rowNumber) {
            this.rowNumber = rowNumber;
        }

        public String getBatch() {
            return batch;
        }

        public void setBatch(String batch) {
            this.batch = batch;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getRating() {
            return rating;
        }

        public void setRating(String rating) {
            this.rating = rating;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getContactNumber() {
            return contactNumber;
        }

        public void setContactNumber(String contactNumber) {
            this.contactNumber = contactNumber;
        }

        public String getOfficialEmail() {
            return officialEmail;
        }

        public void setOfficialEmail(String officialEmail) {
            this.officialEmail = officialEmail;
        }

        public String getPersonalEmail() {
            return personalEmail;
        }

        public void setPersonalEmail(String personalEmail) {
            this.personalEmail = personalEmail;
        }

        public Double getYoeActual() {
            return yoeActual;
        }

        public void setYoeActual(Double yoeActual) {
            this.yoeActual = yoeActual;
        }

        public Double getYoePortrayed() {
            return yoePortrayed;
        }

        public void setYoePortrayed(Double yoePortrayed) {
            this.yoePortrayed = yoePortrayed;
        }

        public String getSkillSet() {
            return skillSet;
        }

        public void setSkillSet(String skillSet) {
            this.skillSet = skillSet;
        }

        public Integer getNoOfInterviews() {
            return noOfInterviews;
        }

        public void setNoOfInterviews(Integer noOfInterviews) {
            this.noOfInterviews = noOfInterviews;
        }

        public Integer getYop() {
            return yop;
        }

        public void setYop(Integer yop) {
            this.yop = yop;
        }

        public String getBatchMentor() { return batchMentor; }
        public void setBatchMentor(String batchMentor) { this.batchMentor = batchMentor; }
        public String getInterviewMentorName() { return interviewMentorName; }
        public void setInterviewMentorName(String interviewMentorName) { this.interviewMentorName = interviewMentorName; }
        public String getClientName() { return clientName; }
        public void setClientName(String clientName) { this.clientName = clientName; }
    }
}