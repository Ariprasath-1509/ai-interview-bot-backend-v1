package com.benchreadiness.screening.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public class CreateBatchRequest {

    @NotBlank
    private String language;

    @NotNull
    private Instant deadline;

    @NotEmpty
    @Valid
    private List<CandidateEntry> candidates;

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public Instant getDeadline() { return deadline; }
    public void setDeadline(Instant deadline) { this.deadline = deadline; }
    public List<CandidateEntry> getCandidates() { return candidates; }
    public void setCandidates(List<CandidateEntry> candidates) { this.candidates = candidates; }

    public static class CandidateEntry {
        @NotBlank
        private String name;
        @NotBlank
        @Email
        private String email;
        private String contactNumber;
        /** JSPIDERS or QSPIDERS */
        private String institute;
        /** Physical location/branch of that institute, e.g. "Bangalore". */
        private String branch;
        private Integer yop;
        private Double experience;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getContactNumber() { return contactNumber; }
        public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }
        public String getInstitute() { return institute; }
        public void setInstitute(String institute) { this.institute = institute; }
        public String getBranch() { return branch; }
        public void setBranch(String branch) { this.branch = branch; }
        public Integer getYop() { return yop; }
        public void setYop(Integer yop) { this.yop = yop; }
        public Double getExperience() { return experience; }
        public void setExperience(Double experience) { this.experience = experience; }
    }
}
