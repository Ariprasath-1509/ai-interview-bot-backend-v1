package com.benchreadiness.auth.dto;

import java.util.List;

public class BulkDownloadRequest {
    private List<String> candidateIds;

    public List<String> getCandidateIds() {
        return candidateIds;
    }

    public void setCandidateIds(List<String> candidateIds) {
        this.candidateIds = candidateIds;
    }
}