package com.financetracker.auth.service;

import com.financetracker.auth.model.RegistrationInvite;
import com.financetracker.auth.repository.RegistrationInviteRepository;
import com.financetracker.common.exception.AuthenticationException;
import com.financetracker.common.exception.EntityNotFoundException;
import com.financetracker.common.security.SecretTokens;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Single-use registration invites (ADR-016). An invite's raw token is returned once at creation and never
 * stored; only its hash is kept. An invite is valid until it expires (7 days) or is used.
 */
@Service
@RequiredArgsConstructor
public class InviteService {

    /** How long a new invite stays valid. */
    public static final Duration LIFETIME = Duration.ofDays(7);

    private final RegistrationInviteRepository invites;

    /** A new invite. Returns the raw token (to put in the link) and the stored row's id/expiry; the token is not kept. */
    @Transactional
    public Created create(UUID createdBy) {
        String token = SecretTokens.newToken();
        Instant now = Instant.now();
        RegistrationInvite invite = invites.save(RegistrationInvite.builder()
            .tokenHash(SecretTokens.hash(token))
            .createdBy(createdBy)
            .createdAt(now)
            .expiresAt(now.plus(LIFETIME))
            .build());
        return new Created(invite.getId(), invite.getExpiresAt(), token);
    }

    @Transactional(readOnly = true)
    public List<RegistrationInvite> list() {
        return invites.findAllByOrderByCreatedAtDesc();
    }

    /** Deletes an invite so its link stops working. */
    @Transactional
    public void revoke(UUID id) {
        RegistrationInvite invite = invites.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Invite not found: " + id));
        invites.delete(invite);
    }

    /**
     * The invite for this token if it is usable now; throws otherwise. Used during registration, before the
     * account is created, so the token is checked but not yet consumed.
     */
    @Transactional(readOnly = true)
    public RegistrationInvite requireUsable(String token) {
        if (token == null || token.isBlank()) {
            throw new AuthenticationException("An invite is required to register");
        }
        RegistrationInvite invite = invites.findByTokenHash(SecretTokens.hash(token.strip()))
            .orElseThrow(() -> new AuthenticationException("This invite link is not valid"));
        if (invite.isUsed()) {
            throw new AuthenticationException("This invite link has already been used");
        }
        if (invite.getExpiresAt().isBefore(Instant.now())) {
            throw new AuthenticationException("This invite link has expired");
        }
        return invite;
    }

    /** Marks an invite as used by a new account. */
    @Transactional
    public void markUsed(RegistrationInvite invite, UUID userId) {
        invite.setUsedAt(Instant.now());
        invite.setUsedBy(userId);
        invites.save(invite);
    }

    /** A freshly created invite's id and expiry, plus its one-time raw token. */
    public record Created(UUID id, Instant expiresAt, String token) { }
}
