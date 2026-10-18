package com.financetracker.integration;

import com.financetracker.auth.model.RegistrationMode;
import com.financetracker.category.model.Category;
import com.financetracker.category.repository.CategoryRepository;
import com.financetracker.settings.service.SettingsService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The registration mode stored in app_settings and the category-ownership guarantee behind the 404s, both
 * against a real PostgreSQL. The 404 mapping itself is covered by the CategoryService unit tests.
 */
@DisplayName("Registration mode and category ownership (Testcontainers)")
class RegistrationAndOwnershipIntegrationTest extends AbstractPostgresIntegrationTest {

    @PersistenceContext private EntityManager em;
    @Autowired private SettingsService settings;
    @Autowired private CategoryRepository categories;

    @Test
    @DisplayName("reads the seeded mode and round-trips a change")
    void registrationModeRoundTrips() {
        assertThat(settings.getRegistrationMode()).isEqualTo(RegistrationMode.INVITE_ONLY);

        settings.setRegistrationMode(RegistrationMode.OPEN);
        assertThat(settings.getRegistrationMode()).isEqualTo(RegistrationMode.OPEN);

        settings.setRegistrationMode(RegistrationMode.CLOSED);
        assertThat(settings.getRegistrationMode()).isEqualTo(RegistrationMode.CLOSED);
    }

    @Test
    @DisplayName("a user sees system categories and their own, never a stranger's")
    void ownershipIsEnforcedInTheQuery() {
        UUID user = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        em.createNativeQuery("INSERT INTO users (id, email, password_hash, role, enabled, created_at) VALUES "
            + "(:a, :ae, 'x', 'USER', true, NOW()), (:b, :be, 'x', 'USER', true, NOW())")
            .setParameter("a", user).setParameter("ae", "owner-" + user + "@example.com")
            .setParameter("b", stranger).setParameter("be", "stranger-" + stranger + "@example.com")
            .executeUpdate();
        UUID mine = UUID.randomUUID();
        UUID theirs = UUID.randomUUID();
        em.createNativeQuery("INSERT INTO categories (id, user_id, name, kind, created_at) VALUES "
            + "(:m, :user, 'My Pets', 'EXPENSE', NOW()), (:t, :stranger, 'Their Secret', 'EXPENSE', NOW())")
            .setParameter("m", mine).setParameter("user", user)
            .setParameter("t", theirs).setParameter("stranger", stranger).executeUpdate();
        em.flush();

        List<Category> visible = categories.findVisibleTo(user);
        List<UUID> ids = visible.stream().map(Category::getId).toList();
        assertThat(ids).contains(mine).doesNotContain(theirs);
        // System categories (user_id NULL, seeded by V5) are visible to everyone.
        assertThat(visible).anyMatch(Category::isSystem);
    }
}
