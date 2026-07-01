package com.benchreadiness.interview.dto;

import java.util.List;

public class BulkCreateInterviewResult {

    private final List<CandidateResult> results;

    public BulkCreateInterviewResult(List<CandidateResult> results) {
        this.results = results;
    }

    public List<CandidateResult> getResults() { return results; }

    public long successCount() {
        return results.stream().filter(r -> "OK".equals(r.getStatus())).count();
    }

    public long failCount() {
        return results.stream().filter(r -> "FAILED".equals(r.getStatus())).count();
    }

    // ── Inner class ───────────────────────────────────────────────────────────

    public static class CandidateResult {
        private final String email;
        private final String status;       // "OK" | "FAILED"
        private final String interviewId;  // non-null when OK
        private final String error;        // non-null when FAILED

        private CandidateResult(String email, String status, String interviewId, String error) {
            this.email = email;
            this.status = status;
            this.interviewId = interviewId;
            this.error = error;
        }

        public static CandidateResult ok(String email, String interviewId) {
            return new CandidateResult(email, "OK", interviewId, null);
        }

        public static CandidateResult failed(String email, String error) {
            return new CandidateResult(email, "FAILED", null, error);
        }

        public String getEmail() { return email; }
        public String getStatus() { return status; }
        public String getInterviewId() { return interviewId; }
        public String getError() { return error; }
    }
}
