package com.benchreadiness.interview.service;

import com.benchreadiness.interview.branch.Branch;
import com.benchreadiness.interview.branch.BranchAccess;
import com.benchreadiness.interview.client.AuthServiceClient;
import com.benchreadiness.interview.dto.ClientDTO;
import com.benchreadiness.interview.dto.CreateInterviewRequest;
import feign.FeignException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class BranchInterviewValidator {

    private final AuthServiceClient authServiceClient;
    private final ClientService clientService;

    public BranchInterviewValidator(AuthServiceClient authServiceClient, ClientService clientService) {
        this.authServiceClient = authServiceClient;
        this.clientService = clientService;
    }

    /**
     * Ensures staff may create the interview and candidate/client branches align when both are present.
     *
     * @return normalized branch to persist on the interview
     */
    public String validateAndResolveBranch(CreateInterviewRequest req, String userId, String userRole) {
        String candidateBranch = resolveCandidateBranch(req, userId);
        String clientBranch = resolveClientBranch(req, userRole);

        if (candidateBranch != null && clientBranch != null
                && !Branch.normalize(candidateBranch).equals(Branch.normalize(clientBranch))) {
            throw new IllegalArgumentException(
                    "Candidate and client must belong to the same branch (candidate: "
                            + Branch.normalize(candidateBranch) + ", client: "
                            + Branch.normalize(clientBranch) + ")");
        }

        String branch = candidateBranch != null ? candidateBranch
                : clientBranch != null ? clientBranch
                : BranchAccess.resolveBranchForCreate(userRole, null);

        branch = Branch.normalize(branch);

        String allowed = BranchAccess.resolveAllowedBranch(userRole);
        if (allowed != null && !allowed.equals(branch)) {
            throw new IllegalArgumentException(
                    "You cannot create interviews for the " + branch + " branch");
        }
        return branch;
    }

    public UUID parseClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(clientId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid clientId: " + clientId);
        }
    }

    private String resolveCandidateBranch(CreateInterviewRequest req, String userId) {
        if (req.getCandidateId() != null && !req.getCandidateId().isBlank()) {
            Map<String, Object> candidate = fetchCandidate(req.getCandidateId(), userId);
            Object branch = candidate.get("branch");
            return branch != null ? branch.toString() : BranchAccess.defaultBranch();
        }
        if (req.getEngineerEmail() != null && !req.getEngineerEmail().isBlank()) {
            try {
                Map<String, Object> user = authServiceClient.getUserByEmail(req.getEngineerEmail().trim());
                if (user != null && user.get("branch") != null) {
                    return user.get("branch").toString();
                }
            } catch (FeignException.NotFound ignored) {
                // Manual email entry without a registered candidate — branch resolved from client or role
            }
        }
        return null;
    }

    private String resolveClientBranch(CreateInterviewRequest req, String userRole) {
        if (req.getClientId() == null || req.getClientId().isBlank()) {
            return null;
        }
        try {
            ClientDTO client = clientService.getClientById(UUID.fromString(req.getClientId()), userRole);
            return client.getBranch() != null ? client.getBranch() : BranchAccess.defaultBranch();
        } catch (NoSuchElementException e) {
            throw new IllegalArgumentException("Client not found or not accessible");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid clientId: " + req.getClientId());
        }
    }

    private Map<String, Object> fetchCandidate(String candidateId, String userId) {
        try {
            Map<String, Object> candidate = authServiceClient.getCandidateById(candidateId, userId);
            if (candidate == null || candidate.isEmpty()) {
                throw new IllegalArgumentException("Candidate not found or not accessible");
            }
            return candidate;
        } catch (FeignException.NotFound e) {
            throw new IllegalArgumentException("Candidate not found or not accessible");
        }
    }
}
