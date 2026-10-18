package com.financetracker.budget.service;

import com.financetracker.budget.model.Budget;
import com.financetracker.budget.repository.BudgetRepository;
import com.financetracker.category.event.CategoryArchivedEvent;
import com.financetracker.category.model.CategoryKind;
import com.financetracker.category.model.CategoryRef;
import com.financetracker.category.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BudgetService")
class BudgetServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID FOOD = UUID.randomUUID();

    @Mock private BudgetRepository budgets;
    @Mock private CategoryService categoryService;

    private BudgetService service;

    @BeforeEach
    void setUp() {
        service = new BudgetService(budgets, categoryService);
        // "now" is 15 Oct 2026, Vietnam time.
        service.setClock(Clock.fixed(ZonedDateTime.of(2026, 10, 15, 9, 0, 0, 0, BudgetService.ZONE).toInstant(),
            BudgetService.ZONE));
    }

    private static Budget row(UUID categoryId, int year, int month, String amount) {
        return Budget.builder().userId(USER).categoryId(categoryId)
            .effectiveFrom(LocalDate.of(year, month, 1)).amount(new BigDecimal(amount)).build();
    }

    @Test
    @DisplayName("the newest change in force wins, and past months keep their own number")
    void newestWinsPerMonth() {
        // Food: 1,000,000 from January, raised to 2,000,000 from March.
        when(budgets.findUpTo(eq(USER), eq(LocalDate.of(2026, 2, 1))))
            .thenReturn(List.of(row(FOOD, 2026, 1, "1000000")));
        when(budgets.findUpTo(eq(USER), eq(LocalDate.of(2026, 4, 1))))
            .thenReturn(List.of(row(FOOD, 2026, 1, "1000000"), row(FOOD, 2026, 3, "2000000")));

        assertThat(service.budgetsFor(USER, YearMonth.of(2026, 2)))
            .containsEntry(Optional.of(FOOD), new BigDecimal("1000000"));
        assertThat(service.budgetsFor(USER, YearMonth.of(2026, 4)))
            .containsEntry(Optional.of(FOOD), new BigDecimal("2000000"));
    }

    @Test
    @DisplayName("amount 0 ends a budget, so the category drops out from that month on")
    void zeroEndsBudget() {
        when(budgets.findUpTo(eq(USER), eq(LocalDate.of(2026, 6, 1))))
            .thenReturn(List.of(row(FOOD, 2026, 1, "1000000"), row(FOOD, 2026, 5, "0")));

        assertThat(service.budgetsFor(USER, YearMonth.of(2026, 6))).doesNotContainKey(Optional.of(FOOD));
    }

    @Test
    @DisplayName("the total budget is keyed under the empty optional")
    void totalBudget() {
        when(budgets.findUpTo(eq(USER), eq(LocalDate.of(2026, 10, 1))))
            .thenReturn(List.of(row(null, 2026, 1, "10000000")));

        Map<Optional<UUID>, BigDecimal> inForce = service.budgetsFor(USER, YearMonth.of(2026, 10));
        assertThat(inForce).containsEntry(Optional.empty(), new BigDecimal("10000000"));
    }

    @Test
    @DisplayName("setting a budget on an expense category stores it from the first of the month")
    void setStoresFromFirstOfMonth() {
        when(categoryService.usableRef(USER, FOOD)).thenReturn(new CategoryRef(FOOD, "Food", CategoryKind.EXPENSE, null));
        when(budgets.findExact(USER, FOOD, LocalDate.of(2026, 11, 1))).thenReturn(Optional.empty());
        when(budgets.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.set(USER, FOOD, new BigDecimal("1500000"), YearMonth.of(2026, 11));

        ArgumentCaptor<Budget> saved = ArgumentCaptor.forClass(Budget.class);
        verify(budgets).save(saved.capture());
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("1500000");
        assertThat(saved.getValue().getEffectiveFrom()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(saved.getValue().getCategoryId()).isEqualTo(FOOD);
    }

    @Test
    @DisplayName("a budget can't be set on a non-expense category, or to a negative amount")
    void rejectsNonExpenseAndNegative() {
        when(categoryService.usableRef(USER, FOOD)).thenReturn(new CategoryRef(FOOD, "Salary", CategoryKind.INCOME, null));
        assertThatThrownBy(() -> service.set(USER, FOOD, new BigDecimal("1000"), YearMonth.of(2026, 11)))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.set(USER, null, new BigDecimal("-5"), YearMonth.of(2026, 11)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("archiving a category ends its budget from next month, leaving this month alone")
    void archivingEndsNextMonth() {
        // A budget is in force next month (November).
        when(budgets.findUpTo(eq(USER), eq(LocalDate.of(2026, 11, 1))))
            .thenReturn(List.of(row(FOOD, 2026, 1, "1000000")));
        when(budgets.findExact(USER, FOOD, LocalDate.of(2026, 11, 1))).thenReturn(Optional.empty());
        when(budgets.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.onCategoryArchived(new CategoryArchivedEvent(FOOD, USER));

        ArgumentCaptor<Budget> saved = ArgumentCaptor.forClass(Budget.class);
        verify(budgets).save(saved.capture());
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("0");
        assertThat(saved.getValue().getEffectiveFrom()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    @DisplayName("archiving a category with no budget next month changes nothing")
    void archivingWithoutBudgetDoesNothing() {
        when(budgets.findUpTo(eq(USER), eq(LocalDate.of(2026, 11, 1)))).thenReturn(List.of());

        service.onCategoryArchived(new CategoryArchivedEvent(FOOD, USER));

        verify(budgets, never()).save(any());
    }
}
