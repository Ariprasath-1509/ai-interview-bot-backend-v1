package com.benchreadiness.auth.repository;

import com.benchreadiness.auth.entity.PasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.Optional;

public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, String> {
    Optional<PasswordResetOtp> findByEmailAndOtpAndUsedFalseAndExpiresAtAfter(String email, String otp, Instant now);
    void deleteByExpiresAtBefore(Instant now);
}
