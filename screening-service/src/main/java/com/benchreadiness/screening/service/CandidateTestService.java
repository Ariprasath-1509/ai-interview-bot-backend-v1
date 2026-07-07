package com.benchreadiness.screening.service;

import com.benchreadiness.screening.dto.SubmitAnswersRequest;
import com.benchreadiness.screening.entity.ScreeningAnswer;
import com.benchreadiness.screening.entity.ScreeningBatch;
import com.benchreadiness.screening.entity.ScreeningCandidate;
import com.benchreadiness.screening.entity.ScreeningQuestion;
import com.benchreadiness.screening.entity.enums.CandidateStage;
import com.benchreadiness.screening.entity.enums.QuestionType;
import com.benchreadiness.screening.repository.ScreeningAnswerRepository;
import com.benchreadiness.screening.repository.ScreeningCandidateRepository;
import com.benchreadiness.screening.repository.ScreeningQuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class CandidateTestService {

    private final ScreeningCandidateRepository candidateRepository;
    private final ScreeningQuestionRepository questionRepository;
    private final ScreeningAnswerRepository answerRepository;
    private final ShuffleService shuffleService;
    private final GradingService gradingService;

    public CandidateTestService(ScreeningCandidateRepository candidateRepository,
                                ScreeningQuestionRepository questionRepository,
                                ScreeningAnswerRepository answerRepository,
                                ShuffleService shuffleService,
                                GradingService gradingService) {
        this.candidateRepository = candidateRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.shuffleService = shuffleService;
        this.gradingService = gradingService;
    }

    @Transactional
    public Map<String, Object> getView(String token) {
        ScreeningCandidate candidate = candidateRepository.findByToken(token)
                .orElseThrow(() -> new NoSuchElementException("Invalid or expired link"));
        ScreeningBatch batch = candidate.getBatch();

        if (candidate.getStage() != CandidateStage.ROUND1_PENDING && candidate.getStage() != CandidateStage.ROUND1_IN_PROGRESS) {
            throw new IllegalStateException("This test has already been submitted and the link is no longer active");
        }
        if (Instant.now().isAfter(batch.getDeadline())) {
            throw new IllegalStateException("This test window has expired");
        }
        if (candidate.getStage() == CandidateStage.ROUND1_PENDING) {
            candidate.setStage(CandidateStage.ROUND1_IN_PROGRESS);
            candidate.setRound1StartedAt(Instant.now());
            candidateRepository.save(candidate);
        }

        List<ScreeningQuestion> questions = questionRepository.findByBatchIdOrderByDisplayIndexAsc(batch.getId());
        List<ScreeningQuestion> shuffled = shuffleService.shuffledOrder(questions, candidate.getShuffleSeed());

        List<Map<String, Object>> questionViews = new ArrayList<>();
        for (ScreeningQuestion q : shuffled) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", q.getId());
            view.put("type", q.getQuestionType().name());
            view.put("prompt", q.getPrompt());
            view.put("marks", q.getMarks());
            if (q.getQuestionType() == QuestionType.MCQ) {
                view.put("options", shuffleService.shuffledOptions(q, candidate.getShuffleSeed()));
            }
            questionViews.add(view);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("candidateName", candidate.getName());
        response.put("candidateEmailMasked", maskEmail(candidate.getEmail()));
        response.put("language", batch.getLanguage());
        response.put("deadline", batch.getDeadline().toString());
        response.put("questions", questionViews);
        response.put("logicalChoiceCount", 2);
        return response;
    }

    @Transactional
    public Map<String, Object> submitAnswers(String token, SubmitAnswersRequest req) throws Exception {
        ScreeningCandidate candidate = candidateRepository.findByToken(token)
                .orElseThrow(() -> new NoSuchElementException("Invalid or expired link"));
        ScreeningBatch batch = candidate.getBatch();

        if (candidate.getStage() != CandidateStage.ROUND1_PENDING && candidate.getStage() != CandidateStage.ROUND1_IN_PROGRESS) {
            throw new IllegalStateException("This test has already been submitted");
        }
        if (Instant.now().isAfter(batch.getDeadline())) {
            throw new IllegalStateException("This test window has expired");
        }
        if (!candidate.getEmail().equalsIgnoreCase(req.getCandidateEmailConfirmation().trim())) {
            throw new IllegalArgumentException("Email confirmation does not match this invite");
        }

        List<ScreeningQuestion> questions = questionRepository.findByBatchIdOrderByDisplayIndexAsc(batch.getId());
        Map<String, ScreeningQuestion> byId = new LinkedHashMap<>();
        questions.forEach(q -> byId.put(q.getId(), q));

        Map<String, String> submitted = new LinkedHashMap<>();
        for (SubmitAnswersRequest.AnswerEntry a : req.getAnswers()) {
            submitted.put(a.getQuestionId(), a.getRawAnswer());
        }

        long logicalAnswered = questions.stream()
                .filter(q -> q.getQuestionType() == QuestionType.LOGICAL)
                .filter(q -> submitted.get(q.getId()) != null && !submitted.get(q.getId()).isBlank())
                .count();
        if (logicalAnswered != 2) {
            throw new IllegalArgumentException("Choose exactly 2 of the 4 logical questions to answer (got " + logicalAnswered + ")");
        }

        double total = 0;
        for (ScreeningQuestion q : questions) {
            String rawAnswer = submitted.get(q.getId());
            if (q.getQuestionType() == QuestionType.LOGICAL && (rawAnswer == null || rawAnswer.isBlank())) {
                continue; // one of the 2 unchosen logical questions
            }

            GradingService.GradeResult result = switch (q.getQuestionType()) {
                case MCQ -> gradingService.gradeMcq(q, rawAnswer);
                case SNIPPET -> gradingService.gradeSnippet(q, rawAnswer);
                case LOGICAL -> gradingService.gradeLogical(q, rawAnswer);
                case PROFICIENCY -> gradingService.gradeProficiency(q, rawAnswer);
            };

            ScreeningAnswer answer = new ScreeningAnswer();
            answer.setCandidate(candidate);
            answer.setQuestion(q);
            answer.setRawAnswer(rawAnswer);
            answer.setScore(result.score);
            answer.setAiFeedback(result.feedback);
            answer.setGradedAt(Instant.now());
            answerRepository.save(answer);

            total += result.score;
        }

        candidate.setRound1Score(total);
        candidate.setStage(CandidateStage.ROUND1_SUBMITTED);
        candidate.setRound1SubmittedAt(Instant.now());
        candidateRepository.save(candidate);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("score", total);
        response.put("maxScore", 35);
        return response;
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.charAt(0) + "***" + email.substring(at);
    }
}
