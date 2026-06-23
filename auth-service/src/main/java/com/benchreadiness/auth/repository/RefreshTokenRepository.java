package com.benchreadiness.auth.repository;

import com.benchreadiness.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    Optional<RefreshToken> findByJtiAndRevokedFalseAndExpiresAtAfter(String jti, Instant now);
    void deleteByExpiresAtBefore(Instant cutoff);
}
