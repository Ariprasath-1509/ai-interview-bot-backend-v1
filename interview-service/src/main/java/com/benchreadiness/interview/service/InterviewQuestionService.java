package com.benchreadiness.interview.service;

import com.benchreadiness.interview.dto.RecordAnswerRequest;
import com.benchreadiness.interview.dto.RecordQuestionRequest;
import com.benchreadiness.interview.entity.InterviewQuestion;
import com.benchreadiness.interview.repository.InterviewQuestionRepository;
import com.benchreadiness.interview.repository.InterviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class InterviewQuestionService {

    private final InterviewQuestionRepository questionRepository;
    private final InterviewRepository interviewRepository;

    public InterviewQuestionService(InterviewQuestionRepository questionRepository,
                                    InterviewRepository interviewRepository) {
        this.questionRepository = questionRepository;
        this.interviewRepository = interviewRepository;
    }

    private void ensureInterviewExists(String interviewId) {
        interviewRepository.findById(interviewId)
            .orElseThrow(() -> new IllegalArgumentException("Interview not found: " + interviewId));
    }

    @Transactional
    public InterviewQuestion recordQuestion(String interviewId, RecordQuestionRequest req) {
        ensureInterviewExists(interviewId);
        if (req.getQuestionText() == null || req.getQuestionText().isBlank()) {
            throw new IllegalArgumentException("questionText is required");
        }

        String questionType = req.getQuestionType();
        if (questionType == null || questionType.isBlank()) {
            questionType = Boolean.TRUE.equals(req.getIsCoding()) ? "CODING" : "TECHNICAL";
        }

        Optional<InterviewQuestion> existing =
            questionRepository.findByInterviewIdAndSlotNumber(interviewId, req.getSlot());

        InterviewQuestion q = existing.orElseGet(InterviewQuestion::new);
        q.setInterviewId(interviewId);
        q.setSlotNumber(req.getSlot());
        q.setQuestionText(req.getQuestionText().trim());
        q.setQuestionType(questionType);
        q.setQuestionBankId(req.getQuestionBankId());
        q.setSource(req.getSource());
        if (q.getAskedAt() == null) {
            q.setAskedAt(LocalDateTime.now());
        }
        return questionRepository.save(q);
    }

    @Transactional
    public InterviewQuestion recordAnswer(String interviewId, RecordAnswerRequest req) {
        ensureInterviewExists(interviewId);
        if (req.getAnswerText() == null || req.getAnswerText().isBlank()) {
            throw new IllegalArgumentException("answerText is required");
        }

        InterviewQuestion q = questionRepository.findByInterviewIdAndSlotNumber(interviewId, req.getSlot())
            .orElseGet(() -> {
                InterviewQuestion created = new InterviewQuestion();
                created.setInterviewId(interviewId);
                created.setSlotNumber(req.getSlot());
                created.setQuestionText("(question pending sync)");
                created.setQuestionType("TECHNICAL");
                created.setAskedAt(LocalDateTime.now());
                return created;
            });

        q.setCandidateAnswer(req.getAnswerText().trim());
        q.setAnsweredAt(LocalDateTime.now());
        return questionRepository.save(q);
    }

    public List<InterviewQuestion> listByInterview(String interviewId) {
        return questionRepository.findByInterviewIdOrderBySlotNumberAsc(interviewId);
    }
}
