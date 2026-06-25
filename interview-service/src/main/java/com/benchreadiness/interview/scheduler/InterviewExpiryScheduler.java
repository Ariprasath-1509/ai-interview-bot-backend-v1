package com.benchreadiness.interview.scheduler;

import com.benchreadiness.interview.client.AuthServiceClient;
import com.benchreadiness.interview.entity.Engineer;
import com.benchreadiness.interview.entity.Interview;
import com.benchreadiness.interview.entity.InterviewStatus;
import com.benchreadiness.interview.repository.EngineerRepository;
import com.benchreadiness.interview.repository.InterviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class InterviewExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(InterviewExpiryScheduler.class);

    private final InterviewRepository interviewRepository;
    private final EngineerRepository engineerRepository;
    private final AuthServiceClient authServiceClient;

    public InterviewExpiryScheduler(InterviewRepository interviewRepository,
                                    EngineerRepository engineerRepository,
                                    AuthServiceClient authServiceClient) {
        this.interviewRepository = interviewRepository;
        this.engineerRepository = engineerRepository;
        this.authServiceClient = authServiceClient;
    }

    @Scheduled(fixedDelay = 300_000) // every 5 minutes
    @Transactional
    public void expireScheduledInterviews() {
        List<Interview> expired = interviewRepository.findByStatusAndExpiresAtIsNotNullAndExpiresAtBefore(
                com.benchreadiness.interview.entity.InterviewStatus.SCHEDULED, Instant.now());
        if (expired.isEmpty()) return;

        log.info("Expiry job: found {} interview(s) past their expiresAt", expired.size());
        for (Interview interview : expired) {
            interview.setStatus(InterviewStatus.EXPIRED);
            interviewRepository.save(interview);
            log.info("Marked interview {} as EXPIRED", interview.getId());

            // Deactivate market candidate credentials
            try {
                Engineer engineer = engineerRepository.findById(interview.getEngineerId()).orElse(null);
                if (engineer != null && engineer.getEmail() != null) {
                    Map<String, Object> profile = authServiceClient.getUserByEmail(engineer.getEmail());
                    if (profile != null && "MARKET".equals(profile.get("source"))) {
                        String candidateUserId = (String) profile.get("id");
                        if (candidateUserId != null) {
                            authServiceClient.setUserActive(candidateUserId, Map.of("active", false));
                            log.info("Deactivated market candidate {} after interview {} expired",
                                    candidateUserId, interview.getId());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Could not deactivate market candidate for expired interview {}: {}",
                        interview.getId(), e.getMessage());
            }
        }
    }
}
