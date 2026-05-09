package com.benchreadiness.auth.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class BulkImportResponse {
    private String sessionId;
    private int totalRows;
    private int validRows;
    private int errorRows;
    private int successCount;
    private int skippedCount;
    private int errorCount;
    private List<ImportDetail> details;
    private List<ValidationError> errors;
    private List<CredentialPreview> credentialPreviews;
    private boolean canProceed;
    private LocalDateTime createdAt;
    private String message;
    private boolean success;

    public BulkImportResponse() {
        this.details = new ArrayList<>();
        this.errors = new ArrayList<>();
        this.credentialPreviews = new ArrayList<>();
        this.success = true;
    }

    public static BulkImportResponse error(String message) {
        BulkImportResponse response = new BulkImportResponse();
        response.success = false;
        response.message = message;
        return response;
    }

    public void addSuccess(int rowNumber, String candidateId) {
        this.details.add(new ImportDetail(rowNumber, "SUCCESS", "Candidate created successfully", candidateId));
        this.successCount++;
    }

    public void addSkipped(int rowNumber, String reason) {
        this.details.add(new ImportDetail(rowNumber, "SKIPPED", reason, null));
        this.skippedCount++;
    }

    public void addError(int rowNumber, String error) {
        this.details.add(new ImportDetail(rowNumber, "ERROR", error, null));
        this.errorCount++;
    }

    // Getters and Setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }
    
    public int getValidRows() { return validRows; }
    public void setValidRows(int validRows) { this.validRows = validRows; }
    
    public int getErrorRows() { return errorRows; }
    public void setErrorRows(int errorRows) { this.errorRows = errorRows; }

    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }

    public int getSkippedCount() { return skippedCount; }
    public void setSkippedCount(int skippedCount) { this.skippedCount = skippedCount; }

    public int getErrorCount() { return errorCount; }
    public void setErrorCount(int errorCount) { this.errorCount = errorCount; }

    public List<ImportDetail> getDetails() { return details; }
    public void setDetails(List<ImportDetail> details) { this.details = details; }
    
    public List<ValidationError> getErrors() { return errors; }
    public void setErrors(List<ValidationError> errors) { this.errors = errors; }
    
    public List<CredentialPreview> getCredentialPreviews() { return credentialPreviews; }
    public void setCredentialPreviews(List<CredentialPreview> credentialPreviews) { this.credentialPreviews = credentialPreviews; }
    
    public boolean isCanProceed() { return canProceed; }
    public void setCanProceed(boolean canProceed) { this.canProceed = canProceed; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public static class ImportDetail {
        private int rowNumber;
        private String status; // SUCCESS, SKIPPED, ERROR
        private String message;
        private String candidateId; // for successful imports

        public ImportDetail() {}

        public ImportDetail(int rowNumber, String status, String message, String candidateId) {
            this.rowNumber = rowNumber;
            this.status = status;
            this.message = message;
            this.candidateId = candidateId;
        }

        // Getters and Setters
        public int getRowNumber() { return rowNumber; }
        public void setRowNumber(int rowNumber) { this.rowNumber = rowNumber; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public String getCandidateId() { return candidateId; }
        public void setCandidateId(String candidateId) { this.candidateId = candidateId; }
    }
    
    public static class ValidationError {
        private int rowNumber;
        private String field;
        private String message;
        private String value;
        private String severity; // ERROR, WARNING
        
        public ValidationError() {}
        
        public ValidationError(int rowNumber, String field, String message, String value, String severity) {
            this.rowNumber = rowNumber;
            this.field = field;
            this.message = message;
            this.value = value;
            this.severity = severity;
        }
        
        // Getters and Setters
        public int getRowNumber() { return rowNumber; }
        public void setRowNumber(int rowNumber) { this.rowNumber = rowNumber; }
        
        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
    }
    
    public static class CredentialPreview {
        private int rowNumber;
        private String name;
        private String source;
        private String batch;
        private String username;
        private String generatedPassword;
        
        public CredentialPreview() {}
        
        // Getters and Setters
        public int getRowNumber() { return rowNumber; }
        public void setRowNumber(int rowNumber) { this.rowNumber = rowNumber; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        
        public String getBatch() { return batch; }
        public void setBatch(String batch) { this.batch = batch; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getGeneratedPassword() { return generatedPassword; }
        public void setGeneratedPassword(String generatedPassword) { this.generatedPassword = generatedPassword; }
    }
}