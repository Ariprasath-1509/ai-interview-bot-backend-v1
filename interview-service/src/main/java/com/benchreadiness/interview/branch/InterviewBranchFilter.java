package com.benchreadiness.interview.branch;

import com.benchreadiness.interview.entity.Interview;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class InterviewBranchFilter {

    private InterviewBranchFilter() {}

    public static List<Interview> filterForRole(List<Interview> interviews, String userId, String userRole) {
        return filterForRole(interviews, userId, userRole, Map.of());
    }

    public static List<Interview> filterForRole(List<Interview> interviews, String userId, String userRole,
                                                Map<String, String> candidateBranchByEngineerId) {
        String allowedBranch = BranchAccess.resolveAllowedBranch(userRole);
        if (allowedBranch == null) {
            return interviews;
        }
        return interviews.stream()
                .filter(i -> allowedBranch.equals(effectiveBranch(i, candidateBranchByEngineerId)))
                .collect(Collectors.toList());
    }

    public static boolean canAccess(Interview interview, String userId, String userRole) {
        return canAccess(interview, userId, userRole, Map.of());
    }

    public static boolean canAccess(Interview interview, String userId, String userRole,
                                    Map<String, String> candidateBranchByEngineerId) {
        return !filterForRole(List.of(interview), userId, userRole, candidateBranchByEngineerId).isEmpty();
    }

    static String effectiveBranch(Interview interview, Map<String, String> candidateBranchByEngineerId) {
        if (candidateBranchByEngineerId != null && interview.getEngineerId() != null) {
            String candidateBranch = candidateBranchByEngineerId.get(interview.getEngineerId());
            if (candidateBranch != null && !candidateBranch.isBlank()) {
                return Branch.normalize(candidateBranch);
            }
        }
        return Branch.normalize(interview.getBranch());
    }
}
