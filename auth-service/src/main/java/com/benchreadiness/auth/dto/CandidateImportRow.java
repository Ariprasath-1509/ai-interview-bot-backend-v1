package com.benchreadiness.auth.dto;

public class CandidateImportRow {
    private int rowNumber;
    private String batch;                 // Column B - Batch (DOH)
    private String batchMentor;          // Column C - Batch Mentor
    private String source;               // Column D - Source
    private String status;               // Column E - Status
    private String rating;               // Column F - Rating
    private String name;                 // Column G - Name
    private String contactNumber;        // Column H - Contact Number
    private String officialEmail;        // Column I - Official Mail ID
    private String personalEmail;        // Column J - Personal Mail ID
    private String yoeActual;           // Column K - YOE - A
    private String yoePortrayed;        // Column L - YOE - P
    private String skillSet;            // Column M - Skill Set
    private String yop;                 // Column N - YOP
    private String noOfInterviews;      // Column O - No of Interviews
    private String interviewMentorName; // Column P - Interview Mentor Name
    private String clientName;          // Column Q - Client Name

    // Constructors
    public CandidateImportRow() {}

    // Getters and Setters
    public int getRowNumber() { return rowNumber; }
    public void setRowNumber(int rowNumber) { this.rowNumber = rowNumber; }

    public String getBatch() { return batch; }
    public void setBatch(String batch) { this.batch = batch; }

    public String getBatchMentor() { return batchMentor; }
    public void setBatchMentor(String batchMentor) { this.batchMentor = batchMentor; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRating() { return rating; }
    public void setRating(String rating) { this.rating = rating; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }

    public String getOfficialEmail() { return officialEmail; }
    public void setOfficialEmail(String officialEmail) { this.officialEmail = officialEmail; }

    public String getPersonalEmail() { return personalEmail; }
    public void setPersonalEmail(String personalEmail) { this.personalEmail = personalEmail; }

    public String getYoeActual() { return yoeActual; }
    public void setYoeActual(String yoeActual) { this.yoeActual = yoeActual; }

    public String getYoePortrayed() { return yoePortrayed; }
    public void setYoePortrayed(String yoePortrayed) { this.yoePortrayed = yoePortrayed; }

    public String getSkillSet() { return skillSet; }
    public void setSkillSet(String skillSet) { this.skillSet = skillSet; }

    public String getYop() { return yop; }
    public void setYop(String yop) { this.yop = yop; }

    public String getNoOfInterviews() { return noOfInterviews; }
    public void setNoOfInterviews(String noOfInterviews) { this.noOfInterviews = noOfInterviews; }

    public String getInterviewMentorName() { return interviewMentorName; }
    public void setInterviewMentorName(String interviewMentorName) { this.interviewMentorName = interviewMentorName; }

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }
}