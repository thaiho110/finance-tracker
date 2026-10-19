package com.financetracker.notifications.api;

import com.financetracker.auth.model.User;
import com.financetracker.auth.repository.UserRepository;
import com.financetracker.auth.service.JwtService;
import com.financetracker.common.exception.AuthenticationException;
import com.financetracker.notifications.service.NotificationStream;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Live notification stream over SSE (ADR-021). Authenticated by the access token in the query string, because
 * {@code EventSource} cannot send an Authorization header; the path is permitted in SecurityConfig and this
 * controller validates the token itself.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Live notification stream")
public class NotificationStreamController {

    private final NotificationStream stream;
    private final JwtService jwtService;
    private final UserRepository users;

    @GetMapping("/stream")
    @Operation(summary = "Open a live SSE stream of the caller's notifications (token passed as a query param)")
    public SseEmitter stream(@RequestParam String token) {
        String email;
        try {
            email = jwtService.extractUsername(token);
        } catch (RuntimeException e) {
            throw new AuthenticationException("Invalid token", e);
        }
        User user = users.findByEmail(email)
            .orElseThrow(() -> new AuthenticationException("Invalid token"));
        return stream.register(user.getId());
    }
}
