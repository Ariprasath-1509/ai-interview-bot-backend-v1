package com.qb.core.service;

import com.qb.core.dto.CategoryDTO;
import com.qb.core.entity.Category;
import com.qb.core.entity.Question;
import com.qb.core.exception.ResourceNotFoundException;
import com.qb.core.repository.CategoryRepository;
import com.qb.core.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepo;
    private final QuestionRepository questionRepo;

    @Cacheable(value = "categories", key = "'all'")
    @Transactional(readOnly = true)
    public List<CategoryDTO> getAllCategories() {
        return categoryRepo.findAll().stream()
                .map(this::toDTO)
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> getAllCategoryNames() {
        return categoryRepo.findAll().stream()
                .map(Category::getName)
                .sorted()
                .toList();
    }

    @CacheEvict(value = "categories", allEntries = true)
    @Transactional
    public CategoryDTO createCategory(String name, String interviewType) {
        if (categoryRepo.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Category already exists: " + name);
        }
        Category cat = categoryRepo.save(
                Category.builder().name(name).interviewType(interviewType).build()
        );
        return toDTO(cat);
    }

    @CacheEvict(value = "categories", allEntries = true)
    @Transactional
    public CategoryDTO renameCategory(UUID id, String newName) {
        Category cat = categoryRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
        if ("General".equalsIgnoreCase(cat.getName())) {
            throw new IllegalArgumentException("General category cannot be renamed.");
        }
        cat.setName(newName);
        return toDTO(categoryRepo.save(cat));
    }

    @CacheEvict(value = "categories", allEntries = true)
    @Transactional
    public CategoryDTO updateInterviewType(UUID id, String interviewType) {
        Category cat = categoryRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
        cat.setInterviewType(interviewType);
        return toDTO(categoryRepo.save(cat));
    }

    @CacheEvict(value = {"categories", "questions"}, allEntries = true)
    @Transactional
    public void deleteCategory(UUID id) {
        Category cat = categoryRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
        if ("General".equalsIgnoreCase(cat.getName())) {
            throw new IllegalArgumentException("General category cannot be deleted.");
        }

        // Reassign all questions in this category to General
        Category general = categoryRepo.findByNameIgnoreCase("General")
                .orElseThrow(() -> new IllegalStateException("General category not found"));

        List<Question> affected = questionRepo.findByCategoryId(id);
        affected.forEach(q -> q.setCategory(general));
        questionRepo.saveAll(affected);

        categoryRepo.delete(cat);
    }

    public Category findOrCreateByName(String name) {
        return categoryRepo.findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    // Try to create a new category with default interview type (shared)
                    Category newCategory = Category.builder()
                            .name(name)
                            .interviewType("shared") // default - works for all interview types
                            .build();
                    try {
                        return categoryRepo.save(newCategory);
                    } catch (Exception e) {
                        // If creation fails (e.g., unique constraint), fetch the existing one
                        return categoryRepo.findByNameIgnoreCase(name)
                                .orElseGet(() -> {
                                    // Fallback to General
                                    return categoryRepo.findByNameIgnoreCase("General")
                                            .orElse(null);
                                });
                    }
                });
    }

    private CategoryDTO toDTO(Category c) {
        long count = questionRepo.countByCategoryId(c.getId());
        return new CategoryDTO(c.getId(), c.getName(), c.getInterviewType(), count, c.getCreatedAt());
    }
}
