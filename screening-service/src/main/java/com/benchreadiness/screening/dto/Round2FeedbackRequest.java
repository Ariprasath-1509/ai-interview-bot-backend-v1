package com.benchreadiness.screening.dto;

import com.benchreadiness.screening.entity.enums.RoundDecision;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** Round 2 (F2F Technical) feedback — recruiter enters free-text notes plus a single total out of 35. */
public class Round2FeedbackRequest {

    private String strengths;
    private String weaknesses;
    private String practical;
    private String improvements;

    @DecimalMin(value = "0", message = "Marks must be between 0 and 35")
    @DecimalMax(value = "35", message = "Marks must be between 0 and 35")
    private Double marks;

    @NotNull
    private RoundDecision result;

    public String getStrengths() { return strengths; }
    public void setStrengths(String strengths) { this.strengths = strengths; }
    public String getWeaknesses() { return weaknesses; }
    public void setWeaknesses(String weaknesses) { this.weaknesses = weaknesses; }
    public String getPractical() { return practical; }
    public void setPractical(String practical) { this.practical = practical; }
    public String getImprovements() { return improvements; }
    public void setImprovements(String improvements) { this.improvements = improvements; }
    public Double getMarks() { return marks; }
    public void setMarks(Double marks) { this.marks = marks; }
    public RoundDecision getResult() { return result; }
    public void setResult(RoundDecision result) { this.result = result; }
}
