package com.financetracker.integration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the real Flyway migrations (V1→V8) apply cleanly to a fresh PostgreSQL and leave the expected
 * schema: the newest tables exist and the legacy categorisation that V7 drops is gone. This would have
 * caught the V7/V8 scripts failing against a real engine.
 */
@DisplayName("Schema migrations (Testcontainers)")
class SchemaMigrationIntegrationTest extends AbstractPostgresIntegrationTest {

    @PersistenceContext
    private EntityManager em;

    private long tableCount(String table) {
        Object result = em.createNativeQuery(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = :t")
            .setParameter("t", table).getSingleResult();
        return ((Number) result).longValue();
    }

    private long columnCount(String table, String column) {
        Object result = em.createNativeQuery(
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = :t AND column_name = :c")
            .setParameter("t", table).setParameter("c", column).getSingleResult();
        return ((Number) result).longValue();
    }

    @Test
    @DisplayName("creates the budgets, settings and invite tables")
    void newTablesExist() {
        assertThat(tableCount("budgets")).isEqualTo(1);
        assertThat(tableCount("app_settings")).isEqualTo(1);
        assertThat(tableCount("registration_invites")).isEqualTo(1);
        assertThat(tableCount("categories")).isEqualTo(1);
    }

    @Test
    @DisplayName("drops the legacy category column and category_mappings table (V7)")
    void legacyCategorisationGone() {
        assertThat(tableCount("category_mappings")).isZero();
        assertThat(columnCount("transactions", "category")).isZero();
        assertThat(columnCount("transactions", "category_id")).isEqualTo(1);
    }

    @Test
    @DisplayName("seeds the default registration mode as INVITE_ONLY")
    void seedsInviteOnly() {
        Object mode = em.createNativeQuery(
            "SELECT setting_value FROM app_settings WHERE setting_key = 'registration_mode'").getSingleResult();
        assertThat(mode).isEqualTo("INVITE_ONLY");
    }
}
