package com.benchreadiness.auth.masterdata;

import com.benchreadiness.auth.exception.ResourceNotFoundException;
import com.benchreadiness.auth.masterdata.dto.CreateMasterDataRequest;
import com.benchreadiness.auth.masterdata.dto.MasterDataEntryDTO;
import com.benchreadiness.auth.masterdata.dto.UpdateMasterDataRequest;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class MasterDataService {

    private final MasterDataEntryRepository repository;
    private final Map<String, Map<String, MasterDataEntry>> cache = new ConcurrentHashMap<>();

    public MasterDataService(MasterDataEntryRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    @Transactional(readOnly = true)
    public void warmCache() {
        refreshCache();
    }

    @Transactional(readOnly = true)
    public void refreshCache() {
        cache.clear();
        for (MasterDataEntry entry : repository.findAll()) {
            cache.computeIfAbsent(entry.getCategory(), k -> new ConcurrentHashMap<>())
                    .put(normalizeCode(entry.getCode()), entry);
        }
    }

    public String normalizeCode(String code) {
        if (code == null) return null;
        return code.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    public boolean isValid(String category, String code) {
        if (code == null || code.isBlank()) return false;
        Map<String, MasterDataEntry> entries = cache.get(category);
        if (entries == null) return false;
        MasterDataEntry entry = entries.get(normalizeCode(code));
        return entry != null && entry.isActive();
    }

    public void requireValid(String category, String code) {
        if (code == null || code.isBlank()) return;
        if (!isValid(category, code)) {
            throw new IllegalArgumentException("Invalid " + category + " value: " + code);
        }
    }

    public String normalizeAndValidate(String category, String code) {
        if (code == null || code.isBlank()) return null;
        String normalized = normalizeCode(code);
        requireValid(category, normalized);
        return normalized;
    }

    public List<String> getActiveCodes(String category) {
        return listByCategory(category, false).stream()
                .map(MasterDataEntryDTO::code)
                .toList();
    }

    @SuppressWarnings("unchecked")
    public List<String> getAllowedCandidateSources(String adminSourceCode) {
        if (adminSourceCode == null) return List.of();
        Map<String, MasterDataEntry> entries = cache.get(MasterDataCategory.ADMIN_SOURCE);
        if (entries == null) return List.of();
        MasterDataEntry entry = entries.get(normalizeCode(adminSourceCode));
        if (entry == null || entry.getMetadata() == null) return List.of();
        Object allowed = entry.getMetadata().get("allowedCandidateSources");
        if (allowed instanceof List<?> list) {
            return list.stream().map(Object::toString).map(this::normalizeCode).toList();
        }
        return List.of();
    }

    public Map<String, List<MasterDataEntryDTO>> listAllGrouped(boolean includeInactive) {
        return repository.findAll().stream()
                .filter(e -> includeInactive || e.isActive())
                .sorted(Comparator.comparing(MasterDataEntry::getCategory)
                        .thenComparing(MasterDataEntry::getDisplayOrder)
                        .thenComparing(MasterDataEntry::getCode))
                .map(MasterDataEntryDTO::from)
                .collect(Collectors.groupingBy(MasterDataEntryDTO::category, LinkedHashMap::new, Collectors.toList()));
    }

    public List<MasterDataEntryDTO> listByCategory(String category, boolean includeInactive) {
        List<MasterDataEntry> entries = includeInactive
                ? repository.findByCategoryOrderByDisplayOrderAscCodeAsc(category)
                : repository.findByCategoryAndActiveTrueOrderByDisplayOrderAscCodeAsc(category);
        return entries.stream().map(MasterDataEntryDTO::from).toList();
    }

    public MasterDataEntryDTO getByCategoryAndCode(String category, String code) {
        MasterDataEntry entry = repository.findByCategoryAndCodeIgnoreCase(category, normalizeCode(code))
                .orElseThrow(() -> new ResourceNotFoundException("Master data entry not found"));
        return MasterDataEntryDTO.from(entry);
    }

    @Transactional
    public MasterDataEntryDTO create(String category, CreateMasterDataRequest request) {
        String code = normalizeCode(request.getCode());
        if (repository.existsByCategoryAndCodeIgnoreCase(category, code)) {
            throw new IllegalArgumentException("Code already exists in " + category + ": " + code);
        }
        MasterDataEntry entry = new MasterDataEntry();
        entry.setCategory(category);
        entry.setCode(code);
        entry.setLabel(request.getLabel().trim());
        entry.setDisplayOrder(request.getDisplayOrder());
        entry.setActive(request.isActive());
        entry.setMetadata(request.getMetadata());
        MasterDataEntry saved = repository.save(entry);
        refreshCache();
        return MasterDataEntryDTO.from(saved);
    }

    @Transactional
    public MasterDataEntryDTO update(String category, UUID id, UpdateMasterDataRequest request) {
        MasterDataEntry entry = repository.findByCategoryAndId(category, id)
                .orElseThrow(() -> new ResourceNotFoundException("Master data entry not found"));
        if (request.getLabel() != null && !request.getLabel().isBlank()) {
            entry.setLabel(request.getLabel().trim());
        }
        if (request.getDisplayOrder() != null) {
            entry.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getActive() != null) {
            entry.setActive(request.getActive());
        }
        if (request.getMetadata() != null) {
            entry.setMetadata(request.getMetadata());
        }
        MasterDataEntry saved = repository.save(entry);
        refreshCache();
        return MasterDataEntryDTO.from(saved);
    }

    @Transactional
    public void deactivate(String category, UUID id) {
        MasterDataEntry entry = repository.findByCategoryAndId(category, id)
                .orElseThrow(() -> new ResourceNotFoundException("Master data entry not found"));
        entry.setActive(false);
        repository.save(entry);
        refreshCache();
    }
}
