package com.financetracker.integrations.service;

import com.financetracker.integrations.event.OutboundEvent;
import com.financetracker.integrations.model.DeliveryStatus;
import com.financetracker.integrations.model.WebhookDelivery;
import com.financetracker.integrations.model.WebhookSubscription;
import com.financetracker.integrations.repository.WebhookDeliveryRepository;
import com.financetracker.integrations.repository.WebhookSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * Posts an {@link OutboundEvent} to each matching active subscription (ADR-023 §2) and records the delivery.
 * This cut makes a single best-effort attempt with a short timeout; scheduled retry/backoff is a follow-up.
 *
 * <p>The target URL is user-supplied, so this is a potential SSRF vector; an allow-list is left as the ADR's
 * open question.
 */
@Component
@RequiredArgsConstructor
public class WebhookDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookDispatcher.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int HTTP_OK_FAMILY = 2;

    private final WebhookSubscriptionRepository subscriptions;
    private final WebhookDeliveryRepository deliveries;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    @EventListener
    @Transactional
    public void on(OutboundEvent event) {
        for (WebhookSubscription subscription : subscriptions.findByOwnerUserIdAndActiveTrue(event.userId())) {
            if (subscribed(subscription, event.eventType())) {
                deliver(subscription, event);
            }
        }
    }

    private boolean subscribed(WebhookSubscription subscription, String eventType) {
        for (String type : subscription.getEventTypes().split(",")) {
            if (type.strip().equals(eventType)) {
                return true;
            }
        }
        return false;
    }

    private void deliver(WebhookSubscription subscription, OutboundEvent event) {
        String body = "{\"type\":\"" + event.eventType() + "\",\"userId\":\"" + event.userId()
            + "\",\"at\":\"" + Instant.now() + "\"}";
        String signature = HmacSigner.sign(subscription.getSecret(), body);
        WebhookDelivery delivery = WebhookDelivery.builder()
            .subscriptionId(subscription.getId())
            .eventType(event.eventType())
            .status(DeliveryStatus.PENDING)
            .attempts(1)
            .lastAttemptAt(Instant.now())
            .build();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(subscription.getTargetUrl()))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("X-Kept-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            delivery.setResponseCode(response.statusCode());
            delivery.setStatus(response.statusCode() / 100 == HTTP_OK_FAMILY ? DeliveryStatus.DELIVERED : DeliveryStatus.FAILED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            delivery.setStatus(DeliveryStatus.FAILED);
        } catch (RuntimeException | java.io.IOException e) {
            delivery.setStatus(DeliveryStatus.FAILED);
            LOG.warn("Webhook delivery to {} failed: {}", subscription.getTargetUrl(), e.toString());
        }
        deliveries.save(delivery);
    }
}
