package com.benchreadiness.auth.repository;

import com.benchreadiness.auth.entity.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {
    boolean existsByJtiAndExpiresAtAfter(String jti, Instant now);
    void deleteByExpiresAtBefore(Instant cutoff);
}
