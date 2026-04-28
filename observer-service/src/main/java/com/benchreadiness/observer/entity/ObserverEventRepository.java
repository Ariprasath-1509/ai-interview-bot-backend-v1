package com.benchreadiness.observer.entity;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ObserverEventRepository extends JpaRepository<ObserverEvent, String> {
    List<ObserverEvent> findByInterviewIdOrderByCreatedAtDesc(String interviewId, Pageable pageable);
    List<ObserverEvent> findByInterviewIdOrderByCreatedAtDesc(String interviewId);
}
