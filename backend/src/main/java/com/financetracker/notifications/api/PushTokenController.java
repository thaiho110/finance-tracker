package com.financetracker.notifications.api;

import com.financetracker.auth.security.AuthenticatedUser;
import com.financetracker.auth.security.CurrentUser;
import com.financetracker.notifications.dto.RegisterPushTokenRequest;
import com.financetracker.notifications.dto.RemovePushTokenRequest;
import com.financetracker.notifications.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Device push-token registration (ADR-021 §4). Always on, user-scoped. The VAPID public key lets a web
 * client subscribe to Web Push; it is empty until a key is configured, at which point the real transport
 * replaces the logging {@code PushSender}.
 */
@RestController
@RequestMapping("/api/v1/push")
@RequiredArgsConstructor
@Tag(name = "Push", description = "Device push-token registration")
public class PushTokenController {

    private final NotificationService notificationService;

    @Value("${kept.push.vapid.public-key:}")
    private String vapidPublicKey;

    @PostMapping("/tokens")
    @Operation(summary = "Register or refresh this device's push token")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterPushTokenRequest request,
                                         @CurrentUser AuthenticatedUser user) {
        notificationService.registerToken(user.getId(), request.client(), request.transport(), request.token());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/tokens")
    @Operation(summary = "Deregister this device's push token (on logout)")
    public ResponseEntity<Void> remove(@Valid @RequestBody RemovePushTokenRequest request,
                                       @CurrentUser AuthenticatedUser user) {
        notificationService.removeToken(user.getId(), request.token());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/vapid-public-key")
    @Operation(summary = "The Web Push VAPID public key, or empty until one is configured")
    public ResponseEntity<Map<String, String>> vapidPublicKey() {
        return ResponseEntity.ok(Map.of("publicKey", vapidPublicKey));
    }
}
