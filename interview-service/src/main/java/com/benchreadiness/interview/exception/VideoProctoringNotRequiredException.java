package com.benchreadiness.interview.exception;

public class VideoProctoringNotRequiredException extends RuntimeException {

    public VideoProctoringNotRequiredException(String candidateSource) {
        super("Video proctoring is not enabled for candidate source: "
                + (candidateSource != null ? candidateSource : "unknown"));
    }
}
