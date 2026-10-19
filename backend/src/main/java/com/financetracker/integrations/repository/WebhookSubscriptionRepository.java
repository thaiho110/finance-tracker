package com.financetracker.integrations.repository;

import com.financetracker.integrations.model.WebhookSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, UUID> {

    List<WebhookSubscription> findByOwnerUserId(UUID ownerUserId);

    List<WebhookSubscription> findByOwnerUserIdAndActiveTrue(UUID ownerUserId);
}
