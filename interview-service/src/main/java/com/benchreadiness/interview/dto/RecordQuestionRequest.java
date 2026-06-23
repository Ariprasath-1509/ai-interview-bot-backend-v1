package com.benchreadiness.interview.dto;

public class RecordQuestionRequest {
    private int slot;
    private String questionText;
    private String questionType;
    private String questionBankId;
    private String source;
    private Boolean isCoding;

    public int getSlot() { return slot; }
    public void setSlot(int slot) { this.slot = slot; }
    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }
    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public String getQuestionBankId() { return questionBankId; }
    public void setQuestionBankId(String questionBankId) { this.questionBankId = questionBankId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Boolean getIsCoding() { return isCoding; }
    public void setIsCoding(Boolean isCoding) { this.isCoding = isCoding; }
}
