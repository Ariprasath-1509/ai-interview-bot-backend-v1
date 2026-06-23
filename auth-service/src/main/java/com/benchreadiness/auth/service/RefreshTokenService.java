package com.benchreadiness.auth.service;

import com.benchreadiness.auth.entity.RefreshToken;
import com.benchreadiness.auth.entity.User;
import com.benchreadiness.auth.repository.RefreshTokenRepository;
import com.benchreadiness.auth.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               UserRepository userRepository,
                               JwtService jwtService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @Transactional
    public void storeRefreshToken(User user, String refreshJti) {
        RefreshToken entity = new RefreshToken();
        entity.setUserId(user.getId());
        entity.setJti(refreshJti);
        entity.setExpiresAt(Instant.now().plusMillis(jwtService.getRefreshExpiryMs()));
        refreshTokenRepository.save(entity);
    }

    @Transactional
    public Optional<JwtService.TokenPair> refresh(String refreshTokenValue) {
        Optional<JwtService.TokenUser> parsed = jwtService.parseRefreshToken(refreshTokenValue);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }

        JwtService.TokenUser tokenUser = parsed.get();
        Optional<RefreshToken> stored = refreshTokenRepository
                .findByJtiAndRevokedFalseAndExpiresAtAfter(tokenUser.jti(), Instant.now());
        if (stored.isEmpty()) {
            return Optional.empty();
        }

        RefreshToken existing = stored.get();
        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        User user = userRepository.findById(tokenUser.userId()).orElse(null);
        if (user == null) {
            return Optional.empty();
        }

        JwtService.TokenPair pair = jwtService.generateTokenPair(user);
        storeRefreshToken(user, pair.refreshJti());
        return Optional.of(pair);
    }

    @Transactional
    public void revoke(String refreshTokenValue) {
        jwtService.parseClaims(refreshTokenValue).ifPresent(claims -> {
            if (!JwtService.TYPE_REFRESH.equals(claims.type())) {
                return;
            }
            refreshTokenRepository.findByJtiAndRevokedFalseAndExpiresAtAfter(claims.jti(), Instant.now())
                    .ifPresent(token -> {
                        token.setRevoked(true);
                        refreshTokenRepository.save(token);
                    });
        });
    }

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanupExpired() {
        refreshTokenRepository.deleteByExpiresAtBefore(Instant.now());
    }
}
