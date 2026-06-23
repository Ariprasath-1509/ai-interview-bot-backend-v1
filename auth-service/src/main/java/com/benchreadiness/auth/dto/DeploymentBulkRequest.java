package com.benchreadiness.auth.dto;

import java.time.LocalDate;
import java.util.List;

public class DeploymentBulkRequest {
    private List<DeploymentRecord> records;

    public static class DeploymentRecord {
        private String empId;
        private String email;  // Will match against officialEmail or personalEmail
        private String clientName;
        private LocalDate deployedDate;
        private String mentor;  // Optional

        // Getters and setters
        public String getEmpId() { return empId; }
        public void setEmpId(String empId) { this.empId = empId; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getClientName() { return clientName; }
        public void setClientName(String clientName) { this.clientName = clientName; }
        public LocalDate getDeployedDate() { return deployedDate; }
        public void setDeployedDate(LocalDate deployedDate) { this.deployedDate = deployedDate; }
        public String getMentor() { return mentor; }
        public void setMentor(String mentor) { this.mentor = mentor; }
    }

    public List<DeploymentRecord> getRecords() { return records; }
    public void setRecords(List<DeploymentRecord> records) { this.records = records; }
}
