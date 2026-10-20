package com.financetracker.assistant.repository;

import com.financetracker.assistant.model.AssistantThread;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssistantThreadRepository extends JpaRepository<AssistantThread, UUID> {

    List<AssistantThread> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<AssistantThread> findByIdAndUserId(UUID id, UUID userId);
}
