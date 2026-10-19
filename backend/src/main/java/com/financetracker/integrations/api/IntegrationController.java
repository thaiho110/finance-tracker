package com.financetracker.integrations.api;

import com.financetracker.auth.security.AuthenticatedUser;
import com.financetracker.auth.security.CurrentUser;
import com.financetracker.integrations.dto.CreateEndpointRequest;
import com.financetracker.integrations.dto.CreateSubscriptionRequest;
import com.financetracker.integrations.dto.DeliveryResponse;
import com.financetracker.integrations.dto.EndpointResponse;
import com.financetracker.integrations.dto.SubscriptionResponse;
import com.financetracker.integrations.service.IntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Managing the caller's integrations (ADR-023): inbound endpoints, outbound subscriptions and the delivery log.
 * In the INTEGRATIONS module; needs {@code INTEGRATIONS_MANAGE} (enforced by URL in SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/integrations")
@RequiredArgsConstructor
@Tag(name = "Integrations", description = "Manage inbound webhook endpoints and outbound subscriptions")
public class IntegrationController {

    private final IntegrationService integrations;

    @GetMapping("/endpoints")
    @Operation(summary = "List the caller's inbound webhook endpoints")
    public ResponseEntity<List<EndpointResponse>> endpoints(@CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(integrations.listEndpoints(user.getId()).stream().map(EndpointResponse::from).toList());
    }

    @PostMapping("/endpoints")
    @Operation(summary = "Create an inbound webhook endpoint (returns its token and secret)")
    public ResponseEntity<EndpointResponse> createEndpoint(@Valid @RequestBody CreateEndpointRequest request,
                                                           @CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(EndpointResponse.from(integrations.createEndpoint(user.getId(), request.name())));
    }

    @DeleteMapping("/endpoints/{id}")
    @Operation(summary = "Delete an inbound webhook endpoint")
    public ResponseEntity<Void> deleteEndpoint(@PathVariable UUID id, @CurrentUser AuthenticatedUser user) {
        integrations.deleteEndpoint(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/subscriptions")
    @Operation(summary = "List the caller's outbound subscriptions")
    public ResponseEntity<List<SubscriptionResponse>> subscriptions(@CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(
            integrations.listSubscriptions(user.getId()).stream().map(SubscriptionResponse::from).toList());
    }

    @PostMapping("/subscriptions")
    @Operation(summary = "Create an outbound subscription (returns its signing secret)")
    public ResponseEntity<SubscriptionResponse> createSubscription(@Valid @RequestBody CreateSubscriptionRequest request,
                                                                   @CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(SubscriptionResponse.from(
            integrations.createSubscription(user.getId(), request.targetUrl(), request.eventTypes())));
    }

    @DeleteMapping("/subscriptions/{id}")
    @Operation(summary = "Delete an outbound subscription")
    public ResponseEntity<Void> deleteSubscription(@PathVariable UUID id, @CurrentUser AuthenticatedUser user) {
        integrations.deleteSubscription(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/deliveries")
    @Operation(summary = "The recent delivery log for one of the caller's subscriptions")
    public ResponseEntity<List<DeliveryResponse>> deliveries(@RequestParam UUID subscriptionId,
                                                             @CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(
            integrations.listDeliveries(user.getId(), subscriptionId).stream().map(DeliveryResponse::from).toList());
    }
}
