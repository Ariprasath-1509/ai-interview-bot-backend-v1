package com.benchreadiness.interview.controller;

import com.benchreadiness.interview.dto.InterviewQuestionDto;
import com.benchreadiness.interview.entity.Engineer;
import com.benchreadiness.interview.entity.Interview;
import com.benchreadiness.interview.entity.InterviewQuestion;
import com.benchreadiness.interview.repository.EngineerRepository;
import com.benchreadiness.interview.repository.InterviewQuestionRepository;
import com.benchreadiness.interview.repository.InterviewRepository;
import com.benchreadiness.interview.service.InterviewQuestionService;
import com.benchreadiness.interview.service.InterviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Fix 6.3: GET /interviews/{id}/questions was staff-only, so the candidate's next-question call got 403
 * and the repeat-question data stayed empty. Candidates may now read only their own interview, and only
 * a slim projection (no answers). Staff behaviour is unchanged.
 */
class ListQuestionsAccessTest {

    private InterviewRepository interviewRepository;
    private EngineerRepository engineerRepository;
    private InterviewQuestionRepository questionRepository;
    private InterviewController controller;

    @BeforeEach
    void setUp() {
        interviewRepository = mock(InterviewRepository.class);
        engineerRepository = mock(EngineerRepository.class);
        questionRepository = mock(InterviewQuestionRepository.class);

        InterviewService interviewService = new InterviewService(interviewRepository, engineerRepository,
            null, null, null, null, null, null, null, null, null, null, null);
        InterviewQuestionService questionService = new InterviewQuestionService(questionRepository, interviewRepository);
        controller = new InterviewController(interviewService, null, null, null, null, questionService,
            null, null, null, null, null, null);

        Interview interview = mock(Interview.class);
        when(interview.getEngineerId()).thenReturn("eng-1");
        when(interviewRepository.findById("iv-1")).thenReturn(Optional.of(interview));

        Engineer owner = mock(Engineer.class);
        when(owner.getId()).thenReturn("eng-1");
        when(engineerRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        Engineer other = mock(Engineer.class);
        when(other.getId()).thenReturn("eng-2");
        when(engineerRepository.findByEmail("other@example.com")).thenReturn(Optional.of(other));

        InterviewQuestion q = new InterviewQuestion();
        q.setInterviewId("iv-1");
        q.setSlotNumber(1);
        q.setQuestionText("What is a closure?");
        q.setQuestionType("TECHNICAL");
        q.setSource("QUESTION_BANK");
        q.setCandidateAnswer("secret answer");
        when(questionRepository.findByInterviewIdOrderBySlotNumberAsc("iv-1")).thenReturn(List.of(q));
    }

    @Test
    void candidateReadsOwnInterviewSlimProjection() {
        ResponseEntity<?> res = controller.listQuestions("iv-1", "CANDIDATE", "owner@example.com");

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        List<?> body = (List<?>) res.getBody();
        assertThat(body).hasSize(1);
        assertThat(body.get(0)).isInstanceOf(InterviewQuestionService.CandidateQuestionDto.class);
        InterviewQuestionService.CandidateQuestionDto dto = (InterviewQuestionService.CandidateQuestionDto) body.get(0);
        assertThat(dto.slotNumber()).isEqualTo(1);
        assertThat(dto.source()).isEqualTo("QUESTION_BANK");
        assertThat(dto.toString()).doesNotContain("secret answer");
    }

    @Test
    void candidateCannotReadSomeoneElsesInterview() {
        assertThat(controller.listQuestions("iv-1", "CANDIDATE", "other@example.com").getStatusCode().value())
            .isEqualTo(403);
    }

    @Test
    void candidateWithoutEmailOrUnknownEmailOrUnknownInterviewGets403() {
        assertThat(controller.listQuestions("iv-1", "CANDIDATE", null).getStatusCode().value()).isEqualTo(403);
        assertThat(controller.listQuestions("iv-1", "CANDIDATE", "nobody@example.com").getStatusCode().value())
            .isEqualTo(403);
        assertThat(controller.listQuestions("iv-missing", "CANDIDATE", "owner@example.com").getStatusCode().value())
            .isEqualTo(403);
    }

    @Test
    void staffStillGetsFullRowsIncludingAnswers() {
        for (String role : List.of("ADMIN", "TESTING_ADMIN", "SUPER_ADMIN", "RECRUITER", "TESTING_RECRUITER")) {
            ResponseEntity<?> res = controller.listQuestions("iv-1", role, null);
            assertThat(res.getStatusCode().value()).isEqualTo(200);
            List<?> body = (List<?>) res.getBody();
            assertThat(body.get(0)).isInstanceOf(InterviewQuestionDto.class);
            assertThat(((InterviewQuestionDto) body.get(0)).getCandidateAnswer()).isEqualTo("secret answer");
        }
    }
}
