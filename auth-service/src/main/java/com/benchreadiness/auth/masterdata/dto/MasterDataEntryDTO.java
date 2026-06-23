package com.benchreadiness.auth.masterdata.dto;

import com.benchreadiness.auth.masterdata.MasterDataEntry;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record MasterDataEntryDTO(
        UUID id,
        String category,
        String code,
        String label,
        int displayOrder,
        boolean active,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
    public static MasterDataEntryDTO from(MasterDataEntry entry) {
        return new MasterDataEntryDTO(
                entry.getId(),
                entry.getCategory(),
                entry.getCode(),
                entry.getLabel(),
                entry.getDisplayOrder(),
                entry.isActive(),
                entry.getMetadata(),
                entry.getCreatedAt(),
                entry.getUpdatedAt()
        );
    }
}
