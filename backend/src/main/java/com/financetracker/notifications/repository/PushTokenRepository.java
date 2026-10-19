package com.financetracker.notifications.repository;

import com.financetracker.notifications.model.PushToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PushTokenRepository extends JpaRepository<PushToken, UUID> {

    List<PushToken> findByUserId(UUID userId);

    Optional<PushToken> findByToken(String token);
}
