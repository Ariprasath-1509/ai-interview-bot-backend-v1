package com.qb.core.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record EmailLogDTO(
        UUID id,
        UUID sentBy,
        String subject,
        int recipientCount,
        List<String> recipientEmails,
        Map<String, String> filters,
        Instant sentAt
) {}
