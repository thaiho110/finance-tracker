package com.financetracker.assistant.repository;

import com.financetracker.assistant.model.AssistantMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AssistantMessageRepository extends JpaRepository<AssistantMessage, UUID> {

    List<AssistantMessage> findByThreadIdOrderByCreatedAtAsc(UUID threadId);
}
