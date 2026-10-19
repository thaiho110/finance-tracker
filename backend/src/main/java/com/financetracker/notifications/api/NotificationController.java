package com.financetracker.notifications.api;

import com.financetracker.auth.security.AuthenticatedUser;
import com.financetracker.auth.security.CurrentUser;
import com.financetracker.notifications.dto.NotificationResponse;
import com.financetracker.notifications.dto.UnreadCountResponse;
import com.financetracker.notifications.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The in-app notification feed (ADR-021). Always on (no module), user-scoped. The feed is read-only to
 * clients — only features create notifications, server-side.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notification feed and read state")
public class NotificationController {

    private static final int PAGE_SIZE = 50;

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "The caller's notifications, newest first; unread=true for only unread")
    public ResponseEntity<List<NotificationResponse>> feed(@RequestParam(defaultValue = "false") boolean unread,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @CurrentUser AuthenticatedUser user) {
        List<NotificationResponse> rows = notificationService.feed(user.getId(), unread, page, PAGE_SIZE)
            .stream().map(NotificationResponse::from).toList();
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark one notification read")
    public ResponseEntity<Void> read(@PathVariable UUID id, @CurrentUser AuthenticatedUser user) {
        notificationService.markRead(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all of the caller's notifications read")
    public ResponseEntity<Void> readAll(@CurrentUser AuthenticatedUser user) {
        notificationService.markAllRead(user.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/unread-count")
    @Operation(summary = "The caller's unread notification count (badge)")
    public ResponseEntity<UnreadCountResponse> unreadCount(@CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(new UnreadCountResponse(notificationService.unreadCount(user.getId())));
    }
}
