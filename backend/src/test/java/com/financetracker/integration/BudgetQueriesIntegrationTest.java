package com.financetracker.integration;

import com.financetracker.budget.model.Budget;
import com.financetracker.budget.repository.BudgetRepository;
import com.financetracker.transaction.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The budget and overview SQL (the budgets JPQL and the transaction native aggregates) against a real
 * PostgreSQL — the native queries never run on H2, so this is where a dialect or grouping mistake shows up.
 */
@DisplayName("Budget and overview queries (Testcontainers)")
class BudgetQueriesIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID FOOD = UUID.randomUUID();
    private static final UUID SALARY = UUID.randomUUID();

    @PersistenceContext private EntityManager em;
    @Autowired private TransactionRepository transactions;
    @Autowired private BudgetRepository budgets;

    private void insertUser(UUID id, String email) {
        em.createNativeQuery("INSERT INTO users (id, email, password_hash, role, enabled, created_at) "
            + "VALUES (:id, :email, 'x', 'USER', true, NOW())")
            .setParameter("id", id).setParameter("email", email).executeUpdate();
    }

    private void insertCategory(UUID id, UUID userId, String name, String kind) {
        em.createNativeQuery("INSERT INTO categories (id, user_id, name, kind, created_at) "
            + "VALUES (:id, :userId, :name, :kind, NOW())")
            .setParameter("id", id).setParameter("userId", userId)
            .setParameter("name", name).setParameter("kind", kind).executeUpdate();
    }

    private void insertTransaction(UUID categoryId, String merchant, String amount, LocalDate date) {
        em.createNativeQuery("INSERT INTO transactions "
            + "(id, date, raw_description, clean_merchant, amount, category_id, source_type, is_duplicate, created_by, created_at) "
            + "VALUES (:id, :date, :merchant, :merchant, :amount, :cat, 'MANUAL', false, :user, NOW())")
            .setParameter("id", UUID.randomUUID()).setParameter("date", date)
            .setParameter("merchant", merchant).setParameter("amount", new BigDecimal(amount))
            .setParameter("cat", categoryId).setParameter("user", USER).executeUpdate();
    }

    private void insertBudget(UUID categoryId, String amount, LocalDate from) {
        em.createNativeQuery("INSERT INTO budgets (id, user_id, category_id, amount, effective_from, created_at) "
            + "VALUES (:id, :user, :cat, :amount, :from, NOW())")
            .setParameter("id", UUID.randomUUID()).setParameter("user", USER).setParameter("cat", categoryId)
            .setParameter("amount", new BigDecimal(amount)).setParameter("from", from).executeUpdate();
    }

    @BeforeEach
    void seed() {
        insertUser(USER, "it-" + USER + "@example.com");
        insertCategory(FOOD, USER, "Food", "EXPENSE");
        insertCategory(SALARY, USER, "Salary", "INCOME");
        insertTransaction(FOOD, "Highlands", "800000", LocalDate.of(2026, 10, 5));
        insertTransaction(FOOD, "Highlands", "200000", LocalDate.of(2026, 10, 20));
        insertTransaction(SALARY, "Employer", "5000000", LocalDate.of(2026, 10, 1));
        insertTransaction(FOOD, "Old Shop", "999", LocalDate.of(2026, 9, 1)); // previous month, excluded from Oct
        insertBudget(FOOD, "1000000", LocalDate.of(2026, 1, 1));
        em.flush();
    }

    @Test
    @DisplayName("category totals for a month sum expenses and income by kind")
    void categoryTotalsForMonth() {
        List<Object[]> rows = transactions.categoryTotalsForRange(USER,
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(rows).hasSize(2);
        assertThat(rows).anySatisfy(r -> {
            assertThat(r[1]).isEqualTo("Food");
            assertThat(r[2]).isEqualTo("EXPENSE");
            assertThat(new BigDecimal(r[4].toString())).isEqualByComparingTo("1000000");
        });
        assertThat(rows).anySatisfy(r -> {
            assertThat(r[1]).isEqualTo("Salary");
            assertThat(new BigDecimal(r[4].toString())).isEqualByComparingTo("5000000");
        });
    }

    @Test
    @DisplayName("top merchants for a month groups expense spend by merchant")
    void topMerchantsForMonth() {
        List<Object[]> rows = transactions.topMerchantsForRange(USER,
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[0]).isEqualTo("Highlands");
        assertThat(new BigDecimal(rows.get(0)[1].toString())).isEqualByComparingTo("1000000");
        assertThat(((Number) rows.get(0)[2]).longValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("budgets in force up to a month come back newest-first ready")
    void budgetsUpToMonth() {
        List<Budget> rows = budgets.findUpTo(USER, LocalDate.of(2026, 10, 1));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getAmount()).isEqualByComparingTo("1000000");

        Optional<Budget> exact = budgets.findExact(USER, FOOD, LocalDate.of(2026, 1, 1));
        assertThat(exact).isPresent();
    }
}
