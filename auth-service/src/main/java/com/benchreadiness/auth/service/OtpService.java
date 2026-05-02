package com.benchreadiness.auth.service;

import com.benchreadiness.auth.entity.PasswordResetOtp;
import com.benchreadiness.auth.repository.PasswordResetOtpRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;

@Service
public class OtpService {

    private final PasswordResetOtpRepository otpRepository;
    private final SecureRandom random = new SecureRandom();

    public OtpService(PasswordResetOtpRepository otpRepository) {
        this.otpRepository = otpRepository;
    }

    public String generateOtp(String email) {
        String otp = String.format("%06d", random.nextInt(1000000));
        PasswordResetOtp entity = new PasswordResetOtp();
        entity.setEmail(email.toLowerCase());
        entity.setOtp(otp);
        entity.setExpiresAt(Instant.now().plusSeconds(600)); // 10 minutes
        otpRepository.save(entity);
        return otp;
    }

    public boolean validateOtp(String email, String otp) {
        return otpRepository.findByEmailAndOtpAndUsedFalseAndExpiresAtAfter(
            email.toLowerCase(), otp, Instant.now()
        ).isPresent();
    }

    @Transactional
    public void markOtpAsUsed(String email, String otp) {
        otpRepository.findByEmailAndOtpAndUsedFalseAndExpiresAtAfter(
            email.toLowerCase(), otp, Instant.now()
        ).ifPresent(entity -> {
            entity.setUsed(true);
            otpRepository.save(entity);
        });
    }

    @Scheduled(fixedRate = 3600000) // Every hour
    @Transactional
    public void cleanupExpiredOtps() {
        otpRepository.deleteByExpiresAtBefore(Instant.now());
    }
}
