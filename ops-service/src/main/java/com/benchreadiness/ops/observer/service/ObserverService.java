package com.benchreadiness.ops.observer.service;

import com.benchreadiness.ops.observer.dto.FlagRequest;
import com.benchreadiness.ops.observer.dto.InjectRequest;
import com.benchreadiness.ops.observer.entity.ObserverEvent;
import com.benchreadiness.ops.observer.entity.ObserverEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ObserverService {

    private final ObserverEventRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ObserverService(ObserverEventRepository repository) {
        this.repository = repository;
    }

    public ObserverEvent inject(InjectRequest req, String observerUserId) throws Exception {
        ObserverEvent event = new ObserverEvent();
        event.setInterviewId(req.getInterviewId());
        event.setObserverUserId(observerUserId);
        event.setKind(req.getMode());
        event.setPayloadJson(objectMapper.writeValueAsString(Map.of("question", req.getQuestion())));
        return repository.save(event);
    }

    public ObserverEvent flag(FlagRequest req, String observerUserId) throws Exception {
        ObserverEvent event = new ObserverEvent();
        event.setInterviewId(req.getInterviewId());
        event.setObserverUserId(observerUserId);
        event.setKind("FLAG_ANSWER");
        event.setPayloadJson(objectMapper.writeValueAsString(Map.of("note", req.getNote())));
        return repository.save(event);
    }

    public List<ObserverEvent> getEventsByInterview(String interviewId, int limit) {
        return repository.findByInterviewIdOrderByCreatedAtDesc(interviewId, PageRequest.of(0, limit));
    }

    public List<ObserverEvent> getAllEventsByInterview(String interviewId) {
        return repository.findByInterviewIdOrderByCreatedAtDesc(interviewId);
    }
}
