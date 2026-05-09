package com.qb.ai.dto;

/**
 * Response after committing digest data to the database.
 */
public record DigestCommitResponse(
        int sessionsCreated,
        int questionsCreated,
        int questionsLinked,
        int tagsCreated
) {}
