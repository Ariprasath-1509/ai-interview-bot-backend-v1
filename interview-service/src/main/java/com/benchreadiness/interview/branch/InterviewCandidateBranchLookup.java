package com.benchreadiness.interview.branch;

import com.benchreadiness.interview.entity.Interview;
import com.benchreadiness.interview.repository.EngineerRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class InterviewCandidateBranchLookup {

    private final EngineerRepository engineerRepository;

    public InterviewCandidateBranchLookup(EngineerRepository engineerRepository) {
        this.engineerRepository = engineerRepository;
    }

    public Map<String, String> forInterviews(List<Interview> interviews) {
        if (interviews == null || interviews.isEmpty()) {
            return Map.of();
        }
        Set<String> engineerIds = interviews.stream()
                .map(Interview::getEngineerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return byEngineerIds(engineerIds);
    }

    public Map<String, String> byEngineerIds(Collection<String> engineerIds) {
        if (engineerIds == null || engineerIds.isEmpty()) {
            return Map.of();
        }
        Map<String, String> branches = new HashMap<>();
        for (Object[] row : engineerRepository.findCandidateBranchesByEngineerIds(engineerIds)) {
            if (row[0] != null && row[1] != null) {
                branches.put(row[0].toString(), row[1].toString());
            }
        }
        return branches;
    }
}
