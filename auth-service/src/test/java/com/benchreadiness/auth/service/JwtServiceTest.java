package com.benchreadiness.auth.service;

import com.benchreadiness.auth.entity.User;
import com.benchreadiness.auth.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private static final String SECRET = "test-jwt-secret-change-in-production-min-32-chars";

    @Mock
    private TokenRevocationService tokenRevocationService;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                tokenRevocationService,
                SECRET,
                "benchreadiness-auth",
                "benchreadiness-api",
                1800000,
                604800000
        );
        lenient().when(tokenRevocationService.isRevoked(anyString())).thenReturn(false);
    }

    @Test
    void generateAndParseAccessToken() {
        User user = buildUser();
        JwtService.TokenPair pair = jwtService.generateTokenPair(user);

        assertNotNull(pair.accessToken());
        assertNotNull(pair.refreshToken());
        assertTrue(jwtService.parseAccessToken(pair.accessToken()).isPresent());
        assertTrue(jwtService.parseRefreshToken(pair.refreshToken()).isPresent());
        assertFalse(jwtService.parseAccessToken(pair.refreshToken()).isPresent());
    }

    @Test
    void revokedAccessTokenIsRejected() {
        User user = buildUser();
        JwtService.TokenPair pair = jwtService.generateTokenPair(user);
        when(tokenRevocationService.isRevoked(any())).thenReturn(true);

        assertTrue(jwtService.parseAccessToken(pair.accessToken()).isEmpty());
        assertFalse(jwtService.isTokenActive(pair.accessToken()));
    }

    @Test
    void revokedJtiMakesTokenInactive() {
        User user = buildUser();
        JwtService.TokenPair pair = jwtService.generateTokenPair(user);
        when(tokenRevocationService.isRevoked(pair.accessJti())).thenReturn(true);

        assertFalse(jwtService.isTokenActive(pair.accessToken()));
    }

    private User buildUser() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", "user-1");
        user.setEmail("test@example.com");
        user.setName("Test User");
        user.setRole(UserRole.CANDIDATE);
        return user;
    }
}
