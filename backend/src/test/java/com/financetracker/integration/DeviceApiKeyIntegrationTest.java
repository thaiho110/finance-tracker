package com.financetracker.integration;

import com.financetracker.auth.security.AuthenticatedUser;
import com.financetracker.auth.service.DeviceApiKeyService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Device API keys against a real PostgreSQL with the real V9 migration: a valid key authenticates as its owner,
 * a revoked key and an unknown key authenticate no one, and only the key's hash is ever stored.
 */
@DisplayName("Device API keys (Testcontainers)")
class DeviceApiKeyIntegrationTest extends AbstractPostgresIntegrationTest {

    @PersistenceContext private EntityManager em;
    @Autowired private DeviceApiKeyService deviceApiKeys;

    private UUID seedUser() {
        UUID id = UUID.randomUUID();
        em.createNativeQuery("INSERT INTO users (id, email, password_hash, role, enabled, created_at) "
                + "VALUES (:id, :email, 'x', 'USER', true, NOW())")
            .setParameter("id", id).setParameter("email", "device-owner-" + id + "@example.com")
            .executeUpdate();
        em.flush();
        return id;
    }

    @Test
    @DisplayName("a valid key authenticates as its owner and stores only the hash")
    void validKeyAuthenticatesAsOwner() {
        UUID owner = seedUser();
        DeviceApiKeyService.Created created = deviceApiKeys.create(owner, "Tablet", owner);
        em.flush();

        assertThat(created.key()).startsWith(DeviceApiKeyService.KEY_PREFIX);
        Object storedHash = em.createNativeQuery("SELECT key_hash FROM device_api_keys WHERE id = :id")
            .setParameter("id", created.id()).getSingleResult();
        assertThat(storedHash).isNotEqualTo(created.key());

        AuthenticatedUser principal = deviceApiKeys.authenticate(created.key()).orElseThrow();
        assertThat(principal.getId()).isEqualTo(owner);
        // A device never carries administration authorities, whatever its owner's role grants.
        assertThat(principal.getAuthorities().stream().map(Object::toString))
            .doesNotContain("USER_READ", "ROLE_MANAGE", "FEATURE_MANAGE", "CATEGORY_ADMIN");
    }

    @Test
    @DisplayName("a revoked key authenticates no one")
    void revokedKeyRejected() {
        UUID owner = seedUser();
        DeviceApiKeyService.Created created = deviceApiKeys.create(owner, "Old phone", owner);
        em.flush();

        assertThat(deviceApiKeys.authenticate(created.key())).isPresent();

        deviceApiKeys.revoke(created.id());
        em.flush();
        assertThat(deviceApiKeys.authenticate(created.key())).isEmpty();
    }

    @Test
    @DisplayName("an unknown key authenticates no one")
    void unknownKeyRejected() {
        assertThat(deviceApiKeys.authenticate(DeviceApiKeyService.KEY_PREFIX + "does-not-exist")).isEmpty();
    }
}
