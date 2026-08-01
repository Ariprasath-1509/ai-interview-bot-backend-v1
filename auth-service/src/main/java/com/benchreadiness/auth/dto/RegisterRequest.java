package com.benchreadiness.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class RegisterRequest {

    @NotBlank
    private String name;

    @NotBlank @Email
    private String email;

    @NotBlank @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank
    private String contactNumber;

    private String officialEmail;

    private String personalEmail;

    private String batch;

    private String source;

    @NotNull
    private String skillSet;

    /** DEVELOPMENT or TESTING — defaults server-side when absent/invalid. */
    private String branch;

    private BigDecimal yoePortrayed;

    private Integer yop;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }
    public String getOfficialEmail() { return officialEmail; }
    public void setOfficialEmail(String officialEmail) { this.officialEmail = officialEmail; }
    public String getPersonalEmail() { return personalEmail; }
    public void setPersonalEmail(String personalEmail) { this.personalEmail = personalEmail; }
    public String getBatch() { return batch; }
    public void setBatch(String batch) { this.batch = batch; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getSkillSet() { return skillSet; }
    public void setSkillSet(String skillSet) { this.skillSet = skillSet; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public BigDecimal getYoePortrayed() { return yoePortrayed; }
    public void setYoePortrayed(BigDecimal yoePortrayed) { this.yoePortrayed = yoePortrayed; }
    public Integer getYop() { return yop; }
    public void setYop(Integer yop) { this.yop = yop; }
}
