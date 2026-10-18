package com.financetracker.auth.service;

import com.financetracker.access.model.Permission;
import com.financetracker.access.service.AccessService;
import com.financetracker.auth.model.DeviceApiKey;
import com.financetracker.auth.model.User;
import com.financetracker.auth.repository.DeviceApiKeyRepository;
import com.financetracker.auth.repository.UserRepository;
import com.financetracker.auth.security.AuthenticatedUser;
import com.financetracker.common.security.SecretTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceApiKeyService")
class DeviceApiKeyServiceTest {

    @Mock private DeviceApiKeyRepository keys;
    @Mock private UserRepository users;
    @Mock private AccessService accessService;

    private DeviceApiKeyService service() {
        return new DeviceApiKeyService(keys, users, accessService);
    }

    private User enabledOwner(UUID id) {
        return User.builder().id(id).email("owner@example.com").passwordHash("x").role("ADMIN").enabled(true).build();
    }

    @Test
    @DisplayName("creates a key, stores only the hash, and returns the raw key once with an ftk_ prefix")
    void createsHashedKey() {
        UUID ownerId = UUID.randomUUID();
        when(users.findById(ownerId)).thenReturn(Optional.of(enabledOwner(ownerId)));
        when(keys.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DeviceApiKeyService.Created created = service().create(ownerId, "  Tablet  ", UUID.randomUUID());

        ArgumentCaptor<DeviceApiKey> saved = ArgumentCaptor.forClass(DeviceApiKey.class);
        verify(keys).save(saved.capture());
        assertThat(created.key()).startsWith(DeviceApiKeyService.KEY_PREFIX);
        assertThat(created.label()).isEqualTo("Tablet");
        assertThat(saved.getValue().getKeyHash()).isEqualTo(SecretTokens.hash(created.key()));
        assertThat(saved.getValue().getKeyHash()).isNotEqualTo(created.key());
        assertThat(saved.getValue().getUserId()).isEqualTo(ownerId);
    }

    @Test
    @DisplayName("a valid key authenticates as the owner with data-plane permissions only, never admin ones")
    void authenticatesWithBoundedPermissions() {
        UUID ownerId = UUID.randomUUID();
        User owner = enabledOwner(ownerId);
        DeviceApiKey key = DeviceApiKey.builder().id(UUID.randomUUID()).userId(ownerId)
            .keyHash(SecretTokens.hash(DeviceApiKeyService.KEY_PREFIX + "abc")).createdAt(Instant.now()).build();
        when(keys.findByKeyHash(SecretTokens.hash(DeviceApiKeyService.KEY_PREFIX + "abc"))).thenReturn(Optional.of(key));
        when(users.findById(ownerId)).thenReturn(Optional.of(owner));
        // The owner is an admin with everything; the device must still only get the data-plane subset.
        when(accessService.effectivePermissions(ownerId, "ADMIN")).thenReturn(EnumSet.allOf(Permission.class));

        Optional<AuthenticatedUser> principal = service().authenticate(DeviceApiKeyService.KEY_PREFIX + "abc");

        assertThat(principal).isPresent();
        Set<String> authorities = principal.get().getAuthorities().stream()
            .map(Object::toString).collect(Collectors.toSet());
        assertThat(authorities).contains(Permission.TRANSACTION_WRITE.name(), Permission.RECEIPT_SCAN.name(),
            Permission.CSV_IMPORT.name());
        assertThat(authorities).doesNotContain(Permission.USER_READ.name(), Permission.ROLE_MANAGE.name(),
            Permission.FEATURE_MANAGE.name(), Permission.CATEGORY_ADMIN.name(), Permission.USER_CREATE.name());
    }

    @Test
    @DisplayName("records last_used_at the first time, then not again within a minute")
    void touchesLastUsedAtAtMostOncePerMinute() {
        UUID ownerId = UUID.randomUUID();
        String raw = DeviceApiKeyService.KEY_PREFIX + "abc";
        DeviceApiKey fresh = DeviceApiKey.builder().id(UUID.randomUUID()).userId(ownerId)
            .keyHash(SecretTokens.hash(raw)).createdAt(Instant.now()).build();
        when(keys.findByKeyHash(SecretTokens.hash(raw))).thenReturn(Optional.of(fresh));
        when(users.findById(ownerId)).thenReturn(Optional.of(enabledOwner(ownerId)));
        when(accessService.effectivePermissions(any(), any())).thenReturn(Set.of());

        service().authenticate(raw);
        verify(keys).save(any());
        assertThat(fresh.getLastUsedAt()).isNotNull();

        // Used again just now: no second write.
        fresh.setLastUsedAt(Instant.now());
        service().authenticate(raw);
        verify(keys).save(any()); // still only the one call from the first authenticate
    }

    @Test
    @DisplayName("a revoked, unknown or blank key authenticates no one")
    void rejectsRevokedUnknownOrBlank() {
        DeviceApiKeyService service = service();
        assertThat(service.authenticate(null)).isEmpty();
        assertThat(service.authenticate("   ")).isEmpty();

        when(keys.findByKeyHash(any())).thenReturn(Optional.empty());
        assertThat(service.authenticate("ftk_unknown")).isEmpty();

        DeviceApiKey revoked = DeviceApiKey.builder().id(UUID.randomUUID()).userId(UUID.randomUUID())
            .keyHash(SecretTokens.hash("ftk_revoked")).createdAt(Instant.now().minus(1, ChronoUnit.DAYS))
            .revokedAt(Instant.now()).build();
        when(keys.findByKeyHash(SecretTokens.hash("ftk_revoked"))).thenReturn(Optional.of(revoked));
        assertThat(service.authenticate("ftk_revoked")).isEmpty();
    }
}
