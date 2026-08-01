package com.benchreadiness.interview.org;

import com.benchreadiness.interview.entity.Interview;

import java.util.List;
import java.util.stream.Collectors;

/** Mirrors {@link com.benchreadiness.interview.branch.InterviewBranchFilter} for the org (tenant) dimension. */
public final class InterviewOrgFilter {

    private InterviewOrgFilter() {}

    public static List<Interview> filterForRole(List<Interview> interviews, String userRole) {
        String allowedOrg = OrgAccess.resolveAllowedOrg(userRole);
        if (allowedOrg == null) {
            return interviews;
        }
        return interviews.stream()
                .filter(i -> allowedOrg.equalsIgnoreCase(i.getOrgCode()))
                .collect(Collectors.toList());
    }

    public static boolean canAccess(Interview interview, String userRole) {
        return !filterForRole(List.of(interview), userRole).isEmpty();
    }
}
