package com.benchreadiness.auth.dto;

import java.time.LocalDateTime;
import java.util.List;

public class BulkImportResponse {
    private String sessionId;
    private int totalRows;
    private int validRows;
    private int errorRows;
    private List<ValidationError> errors;
    private List<CredentialPreview> credentialPreviews;
    private boolean canProceed;
    private LocalDateTime createdAt;

    public BulkImportResponse() {}

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getValidRows() {
        return validRows;
    }

    public void setValidRows(int validRows) {
        this.validRows = validRows;
    }

    public int getErrorRows() {
        return errorRows;
    }

    public void setErrorRows(int errorRows) {
        this.errorRows = errorRows;
    }

    public List<ValidationError> getErrors() {
        return errors;
    }

    public void setErrors(List<ValidationError> errors) {
        this.errors = errors;
    }

    public List<CredentialPreview> getCredentialPreviews() {
        return credentialPreviews;
    }

    public void setCredentialPreviews(List<CredentialPreview> credentialPreviews) {
        this.credentialPreviews = credentialPreviews;
    }

    public boolean isCanProceed() {
        return canProceed;
    }

    public void setCanProceed(boolean canProceed) {
        this.canProceed = canProceed;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
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

        public int getRowNumber() {
            return rowNumber;
        }

        public void setRowNumber(int rowNumber) {
            this.rowNumber = rowNumber;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }
    }

    public static class CredentialPreview {
        private int rowNumber;
        private String name;
        private String username;
        private String generatedPassword;
        private String source;
        private String batch;

        public CredentialPreview() {}

        public int getRowNumber() {
            return rowNumber;
        }

        public void setRowNumber(int rowNumber) {
            this.rowNumber = rowNumber;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getGeneratedPassword() {
            return generatedPassword;
        }

        public void setGeneratedPassword(String generatedPassword) {
            this.generatedPassword = generatedPassword;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getBatch() {
            return batch;
        }

        public void setBatch(String batch) {
            this.batch = batch;
        }
    }
}