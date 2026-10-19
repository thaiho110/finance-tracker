package com.financetracker.integrations.service;

import com.financetracker.common.exception.AuthenticationException;
import com.financetracker.events.DomainEvents;
import com.financetracker.integrations.event.OutboundEvent;
import com.financetracker.integrations.model.WebhookEndpoint;
import com.financetracker.integrations.repository.WebhookEndpointRepository;
import com.financetracker.notifications.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookInboundService Unit Tests")
class WebhookInboundServiceTest {

    @Mock private WebhookEndpointRepository endpoints;
    @Mock private DomainEvents events;
    @Mock private NotificationService notifications;

    private WebhookInboundService service;

    private final UUID owner = UUID.randomUUID();
    private static final String SECRET = "s3cr3t";
    private static final String PAYLOAD = "{\"door\":\"open\"}";

    @BeforeEach
    void setUp() {
        service = new WebhookInboundService(endpoints, events, notifications);
        WebhookEndpoint endpoint = WebhookEndpoint.builder()
            .id(UUID.randomUUID()).ownerUserId(owner).name("Front door").token("tok")
            .secret(SECRET).eventType("integration.trigger").active(true).build();
        when(endpoints.findByToken("tok")).thenReturn(Optional.of(endpoint));
    }

    @Test
    @DisplayName("A valid signature raises an outbound event and notifies the owner")
    void validSignature() {
        long ts = Instant.now().getEpochSecond();
        String signature = HmacSigner.sign(SECRET, ts + "." + PAYLOAD);

        service.handle("tok", String.valueOf(ts), signature, PAYLOAD.getBytes(StandardCharsets.UTF_8));

        verify(events).publish(any(OutboundEvent.class));
        verify(notifications).notify(eq(owner), eq("webhook.triggered"), any(), any(), any());
    }

    @Test
    @DisplayName("A bad signature is rejected and raises nothing")
    void badSignature() {
        long ts = Instant.now().getEpochSecond();

        assertThatThrownBy(() -> service.handle("tok", String.valueOf(ts), "deadbeef",
            PAYLOAD.getBytes(StandardCharsets.UTF_8))).isInstanceOf(AuthenticationException.class);

        verify(events, never()).publish(any());
        verify(notifications, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("A stale timestamp is rejected")
    void staleTimestamp() {
        long old = Instant.now().getEpochSecond() - 10_000;
        String signature = HmacSigner.sign(SECRET, old + "." + PAYLOAD);

        assertThatThrownBy(() -> service.handle("tok", String.valueOf(old), signature,
            PAYLOAD.getBytes(StandardCharsets.UTF_8))).isInstanceOf(AuthenticationException.class);
    }
}
