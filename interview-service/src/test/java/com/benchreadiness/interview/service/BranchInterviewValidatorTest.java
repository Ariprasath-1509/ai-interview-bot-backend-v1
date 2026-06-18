package com.benchreadiness.interview.service;

import com.benchreadiness.interview.branch.BranchAccess;
import com.benchreadiness.interview.client.AuthServiceClient;
import com.benchreadiness.interview.dto.ClientDTO;
import com.benchreadiness.interview.dto.CreateInterviewRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BranchInterviewValidatorTest {

    @Mock
    private AuthServiceClient authServiceClient;

    @Mock
    private ClientService clientService;

    private BranchInterviewValidator validator;

    @BeforeEach
    void setUp() {
        validator = new BranchInterviewValidator(authServiceClient, clientService);
    }

    @Test
    void validateAndResolveBranch_rejectsCrossBranchCandidateAndClient() {
        CreateInterviewRequest req = new CreateInterviewRequest();
        req.setCandidateId("candidate-1");
        req.setClientId(UUID.randomUUID().toString());

        when(authServiceClient.getCandidateById(eq("candidate-1"), eq("staff-1")))
                .thenReturn(Map.of("branch", "DEVELOPMENT"));
        ClientDTO client = new ClientDTO();
        client.setBranch("TESTING");
        when(clientService.getClientById(any(UUID.class), eq("TESTING_ADMIN")))
                .thenReturn(client);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> validator.validateAndResolveBranch(req, "staff-1", "TESTING_ADMIN"));
        assertTrue(ex.getMessage().contains("same branch"));
    }

    @Test
    void validateAndResolveBranch_allowsMatchingBranches() {
        CreateInterviewRequest req = new CreateInterviewRequest();
        req.setCandidateId("candidate-1");
        req.setClientId(UUID.randomUUID().toString());

        when(authServiceClient.getCandidateById(eq("candidate-1"), eq("staff-1")))
                .thenReturn(Map.of("branch", "TESTING"));
        ClientDTO client = new ClientDTO();
        client.setBranch("TESTING");
        when(clientService.getClientById(any(UUID.class), eq("TESTING_RECRUITER")))
                .thenReturn(client);

        assertEquals("TESTING",
                validator.validateAndResolveBranch(req, "staff-1", "TESTING_RECRUITER"));
    }

    @Test
    void validateAndResolveBranch_developmentAdminCannotCreateTestingInterview() {
        CreateInterviewRequest req = new CreateInterviewRequest();
        req.setClientId(UUID.randomUUID().toString());

        ClientDTO client = new ClientDTO();
        client.setBranch("TESTING");
        when(clientService.getClientById(any(UUID.class), eq("ADMIN")))
                .thenReturn(client);

        assertThrows(IllegalArgumentException.class,
                () -> validator.validateAndResolveBranch(req, "staff-1", "ADMIN"));
    }

    @Test
    void validateAndResolveBranch_defaultsToRoleBranchWhenNoCandidateOrClient() {
        CreateInterviewRequest req = new CreateInterviewRequest();
        assertEquals("DEVELOPMENT",
                validator.validateAndResolveBranch(req, "staff-1", "RECRUITER"));
        assertEquals("TESTING",
                validator.validateAndResolveBranch(req, "staff-1", "TESTING_RECRUITER"));
    }
}
