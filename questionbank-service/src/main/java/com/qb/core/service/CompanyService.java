package com.qb.core.service;

import com.qb.core.dto.CompanyDTO;
import com.qb.core.entity.Company;
import com.qb.core.exception.ResourceNotFoundException;
import com.qb.core.repository.CompanyRepository;
import com.qb.core.repository.QuestionRepository;
import com.qb.core.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepo;
    private final QuestionRepository questionRepo;
    private final SessionRepository  sessionRepo;

    @Cacheable(value = "companies", key = "'all'")
    @Transactional(readOnly = true)
    public List<CompanyDTO> getAllCompanies() {
        return companyRepo.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    @CacheEvict(value = "companies", allEntries = true)
    @Transactional
    public CompanyDTO createCompany(String name) {
        if (companyRepo.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Company already exists: " + name);
        }
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        Company company = companyRepo.save(
                Company.builder().name(name).slug(slug).build()
        );
        return toDTO(company);
    }

    @CacheEvict(value = "companies", allEntries = true)
    @Transactional
    public CompanyDTO renameCompany(String slug, String newName) {
        Company company = companyRepo.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Company", slug));
        company.setName(newName);
        String newSlug = newName.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        company.setSlug(newSlug);
        return toDTO(companyRepo.save(company));
    }

    @CacheEvict(value = "companies", allEntries = true)
    @Transactional
    public void deleteCompany(String slug) {
        Company company = companyRepo.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Company", slug));
        if (!sessionRepo.findByCompanyId(company.getId()).isEmpty()) {
            throw new IllegalStateException("Cannot delete company with existing sessions.");
        }
        companyRepo.delete(company);
    }

    @Transactional(readOnly = true)
    public Company findOrCreateByName(String name) {
        return companyRepo.findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
                    return companyRepo.save(Company.builder().name(name).slug(slug).build());
                });
    }

    private CompanyDTO toDTO(Company c) {
        long questionCount = questionRepo.findByCompanySlug(c.getSlug()).size();
        return new CompanyDTO(c.getId(), c.getName(), c.getSlug(), questionCount, c.getCreatedAt());
    }
}
