package com.benchreadiness.screening.service;

import com.benchreadiness.screening.entity.ScreeningQuestion;
import com.benchreadiness.screening.entity.enums.QuestionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Same underlying question pool for every candidate in a batch, but presented in a distinct
 * per-candidate order (and per-candidate MCQ option order) so simultaneous test-takers can't
 * just copy from each other's screens.
 */
@Service
public class ShuffleService {

    private final ObjectMapper objectMapper;

    public ShuffleService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<ScreeningQuestion> shuffledOrder(List<ScreeningQuestion> questions, long seed) {
        Map<QuestionType, List<ScreeningQuestion>> byType = new LinkedHashMap<>();
        for (QuestionType type : List.of(QuestionType.MCQ, QuestionType.SNIPPET, QuestionType.LOGICAL, QuestionType.PROFICIENCY)) {
            byType.put(type, new ArrayList<>());
        }
        for (ScreeningQuestion q : questions) {
            byType.get(q.getQuestionType()).add(q);
        }

        List<ScreeningQuestion> result = new ArrayList<>();
        for (QuestionType type : List.of(QuestionType.MCQ, QuestionType.SNIPPET, QuestionType.LOGICAL, QuestionType.PROFICIENCY)) {
            List<ScreeningQuestion> group = byType.get(type);
            Collections.shuffle(group, new Random(seed + type.ordinal()));
            result.addAll(group);
        }
        return result;
    }

    public List<String> shuffledOptions(ScreeningQuestion question, long candidateSeed) {
        try {
            List<String> options = new ArrayList<>();
            objectMapper.readTree(question.getOptionsJson()).forEach(n -> options.add(n.asText()));
            long optionSeed = candidateSeed + question.getId().hashCode();
            Collections.shuffle(options, new Random(optionSeed));
            return options;
        } catch (Exception e) {
            return List.of();
        }
    }
}
