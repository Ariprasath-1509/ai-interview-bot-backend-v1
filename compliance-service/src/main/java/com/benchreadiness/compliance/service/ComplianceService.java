package com.benchreadiness.compliance.service;

import com.benchreadiness.compliance.dto.AuditLogRequest;
import com.benchreadiness.compliance.entity.*;
import com.benchreadiness.compliance.repository.AuditLogRepository;
import com.benchreadiness.compliance.repository.RetentionPolicyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ComplianceService {

    private final AuditLogRepository auditLogRepository;
    private final RetentionPolicyRepository retentionPolicyRepository;

    public ComplianceService(AuditLogRepository auditLogRepository,
                              RetentionPolicyRepository retentionPolicyRepository) {
        this.auditLogRepository = auditLogRepository;
        this.retentionPolicyRepository = retentionPolicyRepository;
    }

    public AuditLog record(AuditLogRequest req) {
        AuditLog log = new AuditLog();
        log.setActorId(req.getActorId());
        log.setActorRole(req.getActorRole());
        log.setAction(req.getAction());
        log.setResource(req.getResource());
        log.setResourceId(req.getResourceId());
        log.setDetail(req.getDetail());
        log.setIpAddress(req.getIpAddress());
        return auditLogRepository.save(log);
    }

    public Page<AuditLog> getAuditLogs(int page, int size) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    public List<AuditLog> getLogsByActor(String actorId) {
        return auditLogRepository.findByActorIdOrderByCreatedAtDesc(actorId);
    }

    public List<AuditLog> getLogsByResource(String resourceId) {
        return auditLogRepository.findByResourceIdOrderByCreatedAtDesc(resourceId);
    }

    public List<RetentionPolicy> getRetentionPolicies() {
        return retentionPolicyRepository.findAll();
    }

    public RetentionPolicy upsertRetentionPolicy(String region, int transcriptDays, int audioDays) {
        RetentionPolicy policy = retentionPolicyRepository.findByRegion(region).orElseGet(() -> {
            RetentionPolicy p = new RetentionPolicy();
            p.setRegion(region);
            return p;
        });
        policy.setTranscriptDays(transcriptDays);
        policy.setAudioDays(audioDays);
        return retentionPolicyRepository.save(policy);
    }
}
