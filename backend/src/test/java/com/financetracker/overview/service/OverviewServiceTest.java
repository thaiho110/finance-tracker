package com.financetracker.overview.service;

import com.financetracker.budget.model.BudgetStatus;
import com.financetracker.budget.service.BudgetService;
import com.financetracker.category.service.CategoryService;
import com.financetracker.overview.dto.OverviewResponse;
import com.financetracker.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OverviewService")
class OverviewServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID FOOD = UUID.randomUUID();
    private static final UUID SALARY = UUID.randomUUID();
    private static final UUID MOVING = UUID.randomUUID();
    private static final YearMonth OCT = YearMonth.of(2026, 10);

    @Mock private TransactionRepository transactions;
    @Mock private BudgetService budgetService;
    @Mock private CategoryService categoryService;

    private OverviewService service;

    @BeforeEach
    void setUp() {
        service = new OverviewService(transactions, budgetService, categoryService);
        when(categoryService.refs(any())).thenReturn(Map.of());
        when(transactions.topMerchantsForRange(any(), any(), any())).thenReturn(List.of());
    }

    private static Object[] totalRow(UUID id, String name, String kind, String total) {
        return new Object[] {id, name, kind, null, new BigDecimal(total)};
    }

    private void thisMonth(Object[]... rows) {
        when(transactions.categoryTotalsForRange(eq(USER), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31))))
            .thenReturn(List.of(rows));
    }

    private void lastMonth(Object[]... rows) {
        when(transactions.categoryTotalsForRange(eq(USER), eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30))))
            .thenReturn(List.of(rows));
    }

    @Test
    @DisplayName("income and expenses are bucketed by kind; transfers count in neither")
    void bucketsByKindAndIgnoresTransfers() {
        thisMonth(totalRow(SALARY, "Salary", "INCOME", "5000000"),
            totalRow(FOOD, "Food", "EXPENSE", "800000"),
            totalRow(MOVING, "Moving money", "TRANSFER", "9999999"));
        lastMonth(totalRow(FOOD, "Food", "EXPENSE", "1000000"));
        when(budgetService.budgetsFor(USER, OCT)).thenReturn(Map.of());

        OverviewResponse overview = service.overview(USER, OCT, false);

        assertThat(overview.income()).isEqualByComparingTo("5000000");
        assertThat(overview.expenses()).isEqualByComparingTo("800000");
        assertThat(overview.net()).isEqualByComparingTo("4200000");
        assertThat(overview.previousExpenses()).isEqualByComparingTo("1000000");
        assertThat(overview.previousIncome()).isEqualByComparingTo("0");
        assertThat(overview.categories()).extracting(OverviewResponse.CategoryLine::name)
            .containsExactly("Food"); // only expense categories appear
    }

    @Test
    @DisplayName("a refund (negative amount) in an expense category lowers that category's spending")
    void refundLowersSpending() {
        thisMonth(totalRow(FOOD, "Food", "EXPENSE", "-50000"));
        lastMonth();
        when(budgetService.budgetsFor(USER, OCT)).thenReturn(Map.of(Optional.of(FOOD), new BigDecimal("1000000")));

        OverviewResponse overview = service.overview(USER, OCT, true);

        OverviewResponse.CategoryLine food = overview.categories().get(0);
        assertThat(food.spent()).isEqualByComparingTo("-50000");
        assertThat(food.status()).isEqualTo(BudgetStatus.OK);
        assertThat(overview.expenses()).isEqualByComparingTo("-50000");
    }

    @Test
    @DisplayName("with the feature off there are no budgets, warnings or total budget")
    void budgetsOffHidesEverything() {
        thisMonth(totalRow(FOOD, "Food", "EXPENSE", "800000"));
        lastMonth();

        OverviewResponse overview = service.overview(USER, OCT, false);

        assertThat(overview.budgetsEnabled()).isFalse();
        assertThat(overview.totalBudget()).isNull();
        assertThat(overview.categories().get(0).budget()).isNull();
        assertThat(overview.categories().get(0).status()).isNull();
    }

    @Test
    @DisplayName("the total budget is reported with its spending and status")
    void totalBudgetReported() {
        thisMonth(totalRow(FOOD, "Food", "EXPENSE", "800000"));
        lastMonth();
        when(budgetService.budgetsFor(USER, OCT)).thenReturn(Map.of(Optional.empty(), new BigDecimal("1000000")));

        OverviewResponse overview = service.overview(USER, OCT, true);

        assertThat(overview.totalBudget()).isNotNull();
        assertThat(overview.totalBudget().amount()).isEqualByComparingTo("1000000");
        assertThat(overview.totalBudget().spent()).isEqualByComparingTo("800000");
        assertThat(overview.totalBudget().status()).isEqualTo(BudgetStatus.NEAR);
    }

    @Test
    @DisplayName("warnings turn amber at 80 % and red at 100 %, not at 79 %")
    void statusBoundaries() {
        BigDecimal budget = new BigDecimal("1000000");
        assertThat(BudgetStatus.of(new BigDecimal("790000"), budget)).isEqualTo(BudgetStatus.OK);
        assertThat(BudgetStatus.of(new BigDecimal("800000"), budget)).isEqualTo(BudgetStatus.NEAR);
        assertThat(BudgetStatus.of(new BigDecimal("999999"), budget)).isEqualTo(BudgetStatus.NEAR);
        assertThat(BudgetStatus.of(new BigDecimal("1000000"), budget)).isEqualTo(BudgetStatus.OVER);
        assertThat(BudgetStatus.of(new BigDecimal("1200000"), budget)).isEqualTo(BudgetStatus.OVER);
    }

    @Test
    @DisplayName("a category with a budget but no spending still gets a row")
    void budgetedButUnspentStillShows() {
        thisMonth(); // nothing spent this month
        lastMonth();
        when(budgetService.budgetsFor(USER, OCT)).thenReturn(Map.of(Optional.of(FOOD), new BigDecimal("500000")));
        when(categoryService.refs(Set.of(FOOD)))
            .thenReturn(Map.of(FOOD, new com.financetracker.category.model.CategoryRef(
                FOOD, "Food", com.financetracker.category.model.CategoryKind.EXPENSE, "#abc")));

        OverviewResponse overview = service.overview(USER, OCT, true);

        assertThat(overview.categories()).hasSize(1);
        OverviewResponse.CategoryLine food = overview.categories().get(0);
        assertThat(food.name()).isEqualTo("Food");
        assertThat(food.spent()).isEqualByComparingTo("0");
        assertThat(food.budget()).isEqualByComparingTo("500000");
        assertThat(food.status()).isEqualTo(BudgetStatus.OK);
    }
}
