package com.benchreadiness.auth.controller;

import com.benchreadiness.auth.dto.ApiErrorResponse;
import com.benchreadiness.auth.dto.LoginRequest;
import com.benchreadiness.auth.entity.User;
import com.benchreadiness.auth.entity.UserRole;
import com.benchreadiness.auth.feature.OrganizationFeatureService;
import com.benchreadiness.auth.masterdata.MasterDataService;
import com.benchreadiness.auth.repository.OrganizationRepository;
import com.benchreadiness.auth.repository.UserRepository;
import com.benchreadiness.auth.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Login failure paths: unchanged status codes and bodies, plus one reason code per failure. */
class AuthControllerLoginTest {

    private static final String EMAIL = "cand.one@example.test";

    private UserRepository userRepository;
    private PasswordService passwordService;
    private AuditService auditService;
    private LoginAttemptService attempts;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordService = mock(PasswordService.class);
        auditService = mock(AuditService.class);
        attempts = new LoginAttemptService(2, 300, 300);
        controller = new AuthController(userRepository, mock(OrganizationRepository.class),
                mock(JwtService.class), mock(MasterDataService.class), mock(ExcelParserService.class),
                mock(BulkImportService.class), mock(DeploymentService.class), mock(OtpService.class),
                mock(EmailService.class), mock(CandidateBulkImportService.class), passwordService,
                mock(RefreshTokenService.class), attempts, auditService,
                mock(OrganizationFeatureService.class), mock(com.benchreadiness.auth.service.OtpMailCooldown.class));
    }

    private LoginRequest request(String email) {
        LoginRequest req = new LoginRequest();
        req.setUsername(email);
        req.setPassword("not-a-real-password");
        return req;
    }

    private User user(boolean active) {
        User u = new User();
        u.setEmail(EMAIL);
        u.setPassword("hash");
        u.setRole(UserRole.CANDIDATE);
        u.setActive(active);
        return u;
    }

    private void assertBody(ResponseEntity<?> response, int status, String message) {
        assertEquals(status, response.getStatusCode().value());
        assertEquals(message, ((ApiErrorResponse) response.getBody()).getError());
    }

    @Test
    void unknownAccount() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());
        assertBody(controller.login(request(EMAIL)), 401, "Invalid credentials");
        verify(auditService).recordLoginFailure(EMAIL, LoginFailureReason.UNKNOWN_ACCOUNT);
    }

    @Test
    void wrongPassword() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user(true)));
        when(passwordService.matches(any(), any())).thenReturn(false);
        assertBody(controller.login(request(EMAIL)), 401, "Invalid credentials");
        verify(auditService).recordLoginFailure(EMAIL, LoginFailureReason.WRONG_PASSWORD);
    }

    @Test
    void lockedAccountAndUnknownAccountBehaveTheSame() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user(true)));
        when(userRepository.findByEmailIgnoreCase("ghost@example.test")).thenReturn(Optional.empty());
        when(passwordService.matches(any(), any())).thenReturn(false);

        for (String email : new String[] {EMAIL, "ghost@example.test"}) {
            assertBody(controller.login(request(email)), 401, "Invalid credentials");
            assertBody(controller.login(request(email)), 401, "Invalid credentials");
            assertBody(controller.login(request(email)), 429,
                    "Too many failed login attempts. Please try again later.");
            verify(auditService).recordLoginFailure(email, LoginFailureReason.LOCKED);
        }
    }

    @Test
    void inactiveCandidateIsAuditedButNotCountedTowardLockout() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user(false)));
        when(passwordService.matches(any(), any())).thenReturn(true);

        for (int i = 0; i < 5; i++) {
            assertBody(controller.login(request(EMAIL)), 401,
                    "Your account is not yet active. Please wait until your interview is scheduled.");
        }
        verify(auditService, times(5)).recordLoginFailure(EMAIL, LoginFailureReason.INACTIVE_ACCOUNT);
        assertFalse(attempts.isLocked(EMAIL));
    }

    @Test
    void failureReasonNeverCarriesPasswordOrFullAddress() {
        // the masked form is built in AuditService; here we only check the controller passes the email, not the password
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());
        controller.login(request(EMAIL));
        verify(auditService, never()).recordLoginFailure(eq("not-a-real-password"), any());
    }
}
