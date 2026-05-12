package com.qb.core.service;

import com.qb.core.dto.TagDTO;
import com.qb.core.entity.Tag;
import com.qb.core.exception.ResourceNotFoundException;
import com.qb.core.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepo;

    @Cacheable(value = "tags", key = "'all'")
    @Transactional(readOnly = true)
    public List<TagDTO> getAllTags() {
        return tagRepo.findAll().stream()
                .map(t -> new TagDTO(t.getId(), t.getName()))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();
    }

    @CacheEvict(value = "tags", allEntries = true)
    @Transactional
    public TagDTO createTag(String name) {
        String normalized = name.toLowerCase().trim();
        if (tagRepo.existsByNameIgnoreCase(normalized)) {
            throw new IllegalArgumentException("Tag already exists: " + normalized);
        }
        Tag tag = tagRepo.save(Tag.builder().name(normalized).build());
        return new TagDTO(tag.getId(), tag.getName());
    }

    @CacheEvict(value = "tags", allEntries = true)
    @Transactional
    public void deleteTag(UUID id) {
        Tag tag = tagRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tag", id));
        tagRepo.delete(tag);
    }

    /**
     * Find existing tag or create a new one. Used by digest commit flow.
     */
    @Transactional
    public Tag findOrCreate(String name) {
        String normalized = name.toLowerCase().trim();
        return tagRepo.findByNameIgnoreCase(normalized)
                .orElseGet(() -> tagRepo.save(Tag.builder().name(normalized).build()));
    }
}
