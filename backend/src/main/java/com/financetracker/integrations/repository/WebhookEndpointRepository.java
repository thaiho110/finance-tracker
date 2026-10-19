package com.financetracker.integrations.repository;

import com.financetracker.integrations.model.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {

    Optional<WebhookEndpoint> findByToken(String token);

    List<WebhookEndpoint> findByOwnerUserId(UUID ownerUserId);
}
