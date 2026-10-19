package com.financetracker.notifications.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * One message to one user (ADR-021). Every notification is persisted as the user's in-app feed; a
 * notification whose {@code type} routes to push (see {@code NotificationRouting}) is also sent to the
 * user's devices and recorded with {@code pushed = true}.
 *
 * <p>ADR-021's richer {@code data JSONB} / {@code channels[]} payload is reduced here to a {@code deepLink}
 * (the client route to open) and a {@code pushed} flag. The arbitrary-payload column is deferred until a
 * producer needs it (e.g. meal-call quick-reply actions, ADR-025), when its JSON mapping is chosen
 * deliberately rather than guessed against the Jackson 2/3 classpath.
 */
@Entity
@Table(name = "notifications")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Owned by the producing feature, e.g. {@code meal.call}, {@code budget.over}. */
    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String body;

    /** A client route to open when the notification is tapped, or null. */
    @Column(name = "deep_link", length = 500)
    private String deepLink;

    /** Whether this notification was also delivered to push, in addition to the in-app feed. */
    @Column(nullable = false)
    private boolean pushed;

    /** When the user marked it read, or null while unread. */
    @Column(name = "read_at")
    private Instant readAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
