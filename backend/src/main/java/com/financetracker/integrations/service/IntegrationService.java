package com.financetracker.integrations.service;

import com.financetracker.common.exception.EntityNotFoundException;
import com.financetracker.common.security.SecretTokens;
import com.financetracker.integrations.model.WebhookDelivery;
import com.financetracker.integrations.model.WebhookEndpoint;
import com.financetracker.integrations.model.WebhookSubscription;
import com.financetracker.integrations.repository.WebhookDeliveryRepository;
import com.financetracker.integrations.repository.WebhookEndpointRepository;
import com.financetracker.integrations.repository.WebhookSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Managing a user's integrations (ADR-023): inbound webhook endpoints and outbound subscriptions, plus the
 * delivery log. Needs {@code INTEGRATIONS_MANAGE} (checked at the URL). Each endpoint/subscription gets a random
 * token and secret; the secret is kept so HMAC can be recomputed.
 */
@Service
@RequiredArgsConstructor
public class IntegrationService {

    private static final int DELIVERY_PAGE = 50;

    private final WebhookEndpointRepository endpoints;
    private final WebhookSubscriptionRepository subscriptions;
    private final WebhookDeliveryRepository deliveries;

    @Transactional
    public WebhookEndpoint createEndpoint(UUID userId, String name) {
        return endpoints.save(WebhookEndpoint.builder()
            .ownerUserId(userId)
            .name(name)
            .token(SecretTokens.newToken())
            .secret(SecretTokens.newToken())
            .eventType("integration.trigger")
            .active(true)
            .build());
    }

    @Transactional(readOnly = true)
    public List<WebhookEndpoint> listEndpoints(UUID userId) {
        return endpoints.findByOwnerUserId(userId);
    }

    @Transactional
    public void deleteEndpoint(UUID userId, UUID id) {
        endpoints.delete(ownedEndpoint(userId, id));
    }

    @Transactional
    public WebhookSubscription createSubscription(UUID userId, String targetUrl, List<String> eventTypes) {
        if (targetUrl == null || !(targetUrl.startsWith("http://") || targetUrl.startsWith("https://"))) {
            throw new IllegalArgumentException("targetUrl must be an http(s) URL");
        }
        return subscriptions.save(WebhookSubscription.builder()
            .ownerUserId(userId)
            .targetUrl(targetUrl)
            .secret(SecretTokens.newToken())
            .eventTypes(String.join(",", eventTypes))
            .active(true)
            .build());
    }

    @Transactional(readOnly = true)
    public List<WebhookSubscription> listSubscriptions(UUID userId) {
        return subscriptions.findByOwnerUserId(userId);
    }

    @Transactional
    public void deleteSubscription(UUID userId, UUID id) {
        subscriptions.delete(ownedSubscription(userId, id));
    }

    @Transactional(readOnly = true)
    public List<WebhookDelivery> listDeliveries(UUID userId, UUID subscriptionId) {
        ownedSubscription(userId, subscriptionId);
        return deliveries.findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId, PageRequest.of(0, DELIVERY_PAGE));
    }

    private WebhookEndpoint ownedEndpoint(UUID userId, UUID id) {
        WebhookEndpoint endpoint = endpoints.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Webhook endpoint not found"));
        if (!endpoint.getOwnerUserId().equals(userId)) {
            throw new EntityNotFoundException("Webhook endpoint not found");
        }
        return endpoint;
    }

    private WebhookSubscription ownedSubscription(UUID userId, UUID id) {
        WebhookSubscription subscription = subscriptions.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Subscription not found"));
        if (!subscription.getOwnerUserId().equals(userId)) {
            throw new EntityNotFoundException("Subscription not found");
        }
        return subscription;
    }
}
