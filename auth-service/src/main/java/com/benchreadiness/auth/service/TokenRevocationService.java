package com.benchreadiness.auth.service;

import com.benchreadiness.auth.entity.RevokedToken;
import com.benchreadiness.auth.repository.RevokedTokenRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TokenRevocationService {

    private final RevokedTokenRepository revokedTokenRepository;

    public TokenRevocationService(RevokedTokenRepository revokedTokenRepository) {
        this.revokedTokenRepository = revokedTokenRepository;
    }

    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        return revokedTokenRepository.existsByJtiAndExpiresAtAfter(jti, Instant.now());
    }

    @Transactional
    public void revoke(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank() || expiresAt == null) {
            return;
        }
        if (!revokedTokenRepository.existsById(jti)) {
            revokedTokenRepository.save(new RevokedToken(jti, expiresAt));
        }
    }

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanupExpired() {
        revokedTokenRepository.deleteByExpiresAtBefore(Instant.now());
    }
}
