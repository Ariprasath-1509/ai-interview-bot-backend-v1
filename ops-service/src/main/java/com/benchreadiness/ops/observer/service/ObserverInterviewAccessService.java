package com.benchreadiness.ops.observer.service;

import com.benchreadiness.ops.branch.BranchAccess;
import com.benchreadiness.ops.observer.client.InterviewServiceClient;
import feign.FeignException;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ObserverInterviewAccessService {

    private final InterviewServiceClient interviewServiceClient;

    public ObserverInterviewAccessService(InterviewServiceClient interviewServiceClient) {
        this.interviewServiceClient = interviewServiceClient;
    }

    /**
     * @throws IllegalArgumentException when interview is missing or staff cannot access its branch
     */
    public void assertStaffCanAccessInterview(String interviewId, String userRole) {
        if (userRole == null || userRole.isBlank()) {
            throw new IllegalArgumentException("Staff role required");
        }
        Map<String, Object> interview;
        try {
            interview = interviewServiceClient.getInterview(interviewId);
        } catch (FeignException.NotFound e) {
            throw new IllegalArgumentException("Interview not found");
        } catch (Exception e) {
            throw new IllegalArgumentException("Interview not found");
        }
        if (interview == null || interview.isEmpty()) {
            throw new IllegalArgumentException("Interview not found");
        }
        Object branch = interview.get("branch");
        String interviewBranch = branch != null ? branch.toString() : "DEVELOPMENT";
        if (!BranchAccess.canAccessBranch(userRole, interviewBranch)) {
            throw new IllegalArgumentException("Interview not found");
        }
    }
}
