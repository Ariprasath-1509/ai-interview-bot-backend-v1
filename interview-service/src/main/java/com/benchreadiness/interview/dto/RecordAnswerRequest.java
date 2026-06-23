package com.benchreadiness.interview.dto;

public class RecordAnswerRequest {
    private int slot;
    private String answerText;

    public int getSlot() { return slot; }
    public void setSlot(int slot) { this.slot = slot; }
    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }
}
