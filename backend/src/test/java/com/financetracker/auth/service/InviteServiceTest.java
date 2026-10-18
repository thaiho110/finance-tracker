package com.financetracker.auth.service;

import com.financetracker.auth.model.RegistrationInvite;
import com.financetracker.auth.repository.RegistrationInviteRepository;
import com.financetracker.common.exception.AuthenticationException;
import com.financetracker.common.security.SecretTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InviteService")
class InviteServiceTest {

    @Mock private RegistrationInviteRepository invites;

    private InviteService service() {
        return new InviteService(invites);
    }

    @Test
    @DisplayName("creates an invite, stores only the token hash, and returns the raw token once")
    void createsHashedInvite() {
        when(invites.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InviteService.Created created = service().create(UUID.randomUUID());

        ArgumentCaptor<RegistrationInvite> saved = ArgumentCaptor.forClass(RegistrationInvite.class);
        org.mockito.Mockito.verify(invites).save(saved.capture());
        assertThat(created.token()).isNotBlank();
        assertThat(saved.getValue().getTokenHash()).isEqualTo(SecretTokens.hash(created.token()));
        assertThat(saved.getValue().getTokenHash()).isNotEqualTo(created.token());
        assertThat(saved.getValue().getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("rejects a blank, unknown, used or expired token")
    void rejectsInvalidTokens() {
        InviteService service = service();
        assertThatThrownBy(() -> service.requireUsable(null)).isInstanceOf(AuthenticationException.class);

        when(invites.findByTokenHash(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.requireUsable("nope")).isInstanceOf(AuthenticationException.class);

        RegistrationInvite used = RegistrationInvite.builder()
            .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).usedAt(Instant.now()).build();
        when(invites.findByTokenHash(any())).thenReturn(Optional.of(used));
        assertThatThrownBy(() -> service.requireUsable("used")).isInstanceOf(AuthenticationException.class)
            .hasMessageContaining("already been used");

        RegistrationInvite expired = RegistrationInvite.builder()
            .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS)).build();
        when(invites.findByTokenHash(any())).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.requireUsable("old")).isInstanceOf(AuthenticationException.class)
            .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("accepts a valid token and marks it used by the new account")
    void consumesValidToken() {
        InviteService service = service();
        RegistrationInvite valid = RegistrationInvite.builder()
            .id(UUID.randomUUID()).expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).build();
        when(invites.findByTokenHash(SecretTokens.hash("good"))).thenReturn(Optional.of(valid));

        RegistrationInvite found = service.requireUsable("good");
        assertThat(found).isSameAs(valid);

        UUID userId = UUID.randomUUID();
        service.markUsed(valid, userId);
        assertThat(valid.getUsedBy()).isEqualTo(userId);
        assertThat(valid.getUsedAt()).isNotNull();
    }
}
