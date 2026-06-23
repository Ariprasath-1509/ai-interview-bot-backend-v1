package com.benchreadiness.auth.service;

import com.benchreadiness.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    public static final String CLAIM_TYPE = "typ";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final TokenRevocationService tokenRevocationService;
    private final SecretKey signingKey;
    private final String issuer;
    private final String audience;
    private final long accessExpiryMs;
    private final long refreshExpiryMs;

    public JwtService(
            TokenRevocationService tokenRevocationService,
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.jwt.issuer:benchreadiness-auth}") String issuer,
            @Value("${app.jwt.audience:benchreadiness-api}") String audience,
            @Value("${app.jwt.access-expiry-ms:1800000}") long accessExpiryMs,
            @Value("${app.jwt.refresh-expiry-ms:604800000}") long refreshExpiryMs) {
        this.tokenRevocationService = tokenRevocationService;
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.audience = audience;
        this.accessExpiryMs = accessExpiryMs;
        this.refreshExpiryMs = refreshExpiryMs;
    }

    public TokenPair generateTokenPair(User user) {
        String accessJti = UUID.randomUUID().toString();
        String refreshJti = UUID.randomUUID().toString();
        String accessToken = buildToken(user, accessJti, TYPE_ACCESS, accessExpiryMs);
        String refreshToken = buildToken(user, refreshJti, TYPE_REFRESH, refreshExpiryMs);
        return new TokenPair(accessToken, refreshToken, accessJti, refreshJti, accessExpiryMs / 1000);
    }

    public String generateAccessToken(User user) {
        return buildToken(user, UUID.randomUUID().toString(), TYPE_ACCESS, accessExpiryMs);
    }

    public Optional<TokenUser> parseAccessToken(String token) {
        return parseToken(token, TYPE_ACCESS, true);
    }

    public Optional<TokenUser> parseRefreshToken(String token) {
        return parseToken(token, TYPE_REFRESH, false);
    }

    /** Backward-compatible alias used by HeaderAuthenticationFilter. */
    public Optional<TokenUser> parseToken(String token) {
        return parseAccessToken(token);
    }

    public boolean isTokenActive(String token) {
        return parseAccessToken(token).isPresent();
    }

    public void revokeToken(String token) {
        parseClaims(token).ifPresent(claims -> {
            String jti = claims.jti();
            Instant expiresAt = claims.expiresAt();
            if (jti != null && expiresAt != null) {
                tokenRevocationService.revoke(jti, expiresAt);
            }
        });
    }

    public Optional<ParsedClaims> parseClaims(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .requireAudience(audience)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new ParsedClaims(
                    claims.getId(),
                    claims.getSubject(),
                    claims.get(CLAIM_TYPE, String.class),
                    claims.getExpiration() != null ? claims.getExpiration().toInstant() : null,
                    claims.get("role", String.class)
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public long getRefreshExpiryMs() {
        return refreshExpiryMs;
    }

    private Optional<TokenUser> parseToken(String token, String expectedType, boolean checkRevocation) {
        return parseClaims(token).flatMap(claims -> {
            if (!expectedType.equals(claims.type())) {
                return Optional.empty();
            }
            if (claims.expiresAt() != null && claims.expiresAt().isBefore(Instant.now())) {
                return Optional.empty();
            }
            if (checkRevocation && claims.jti() != null && tokenRevocationService.isRevoked(claims.jti())) {
                return Optional.empty();
            }
            if (claims.userId() == null || claims.role() == null || claims.role().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new TokenUser(claims.userId(), claims.role(), claims.jti()));
        });
    }

    private String buildToken(User user, String jti, String type, long expiryMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiryMs);

        var builder = Jwts.builder()
                .id(jti)
                .issuer(issuer)
                .audience().add(audience).and()
                .subject(user.getId())
                .claim(CLAIM_TYPE, type)
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name());
        if (user.getAdminSource() != null) {
            builder.claim("adminSource", user.getAdminSource());
        }
        if (user.getRole().isStaff()) {
            builder.claim("branch", com.benchreadiness.auth.branch.BranchAccess.resolveStaffBranch(user.getRole()));
        } else if (user.getBranch() != null && !user.getBranch().isBlank()) {
            builder.claim("branch", user.getBranch());
        }
        return builder
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public record TokenPair(String accessToken, String refreshToken, String accessJti, String refreshJti, long expiresInSeconds) {}

    public record TokenUser(String userId, String role, String jti) {
        public TokenUser(String userId, String role) {
            this(userId, role, null);
        }
    }

    public record ParsedClaims(String jti, String userId, String type, Instant expiresAt, String role) {}
}
