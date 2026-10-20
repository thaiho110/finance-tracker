package com.financetracker.integrations.repository;

import com.financetracker.integrations.model.WebhookDelivery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    List<WebhookDelivery> findBySubscriptionIdOrderByCreatedAtDesc(UUID subscriptionId, Pageable pageable);
}
