package com.financetracker.overview.service;

import com.financetracker.budget.dto.BudgetRowResponse;
import com.financetracker.budget.model.BudgetStatus;
import com.financetracker.budget.service.BudgetService;
import com.financetracker.category.dto.CategoryResponse;
import com.financetracker.category.model.CategoryKind;
import com.financetracker.category.service.CategoryService;
import com.financetracker.overview.dto.OverviewResponse;
import com.financetracker.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The monthly overview and the Budgets page figures (ADR-019). Both read spending from transactions, the
 * budgets in force from {@link BudgetService}, and the user's categories; the overview adds warnings and top
 * merchants. Months are calendar months in Vietnam time (see {@link BudgetService#ZONE}).
 */
@Service
@RequiredArgsConstructor
public class OverviewService {

    /** Columns of {@code categoryTotalsForRange}: id, name, kind, color, total. */
    private static final int COL_ID = 0;
    private static final int COL_NAME = 1;
    private static final int COL_KIND = 2;
    private static final int COL_COLOR = 3;
    private static final int COL_TOTAL = 4;

    private final TransactionRepository transactions;
    private final BudgetService budgetService;
    private final CategoryService categoryService;

    /** The month from a {@code YYYY-MM} string, or the current month when it is blank. */
    public YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return budgetService.currentMonth();
        }
        try {
            return YearMonth.parse(month.strip());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Month must look like 2026-10", e);
        }
    }

    public OverviewResponse overview(UUID userId, YearMonth month, boolean budgetsEnabled) {
        MonthTotals current = totals(userId, month);
        MonthTotals previous = totals(userId, month.minusMonths(1));
        Map<Optional<UUID>, BigDecimal> budgets = budgetsEnabled ? budgetService.budgetsFor(userId, month) : Map.of();

        // Every expense category worth a row: one we spent in this month, or one with a budget this month.
        Map<UUID, CategoryAgg> expensesById = current.expensesById();
        Map<UUID, BigDecimal> previousSpent = previous.expenseSpentById();
        List<UUID> ids = new ArrayList<>(expensesById.keySet());
        budgets.keySet().stream().flatMap(Optional::stream).filter(id -> !expensesById.containsKey(id)).forEach(ids::add);
        Map<UUID, com.financetracker.category.model.CategoryRef> refs = categoryService.refs(new java.util.HashSet<>(ids));

        List<OverviewResponse.CategoryLine> lines = new ArrayList<>();
        for (UUID id : ids) {
            CategoryAgg agg = expensesById.get(id);
            BigDecimal spent = agg != null ? agg.total() : BigDecimal.ZERO;
            BigDecimal budget = budgets.get(Optional.of(id));
            String name = agg != null ? agg.name() : refs.containsKey(id) ? refs.get(id).name() : null;
            String color = agg != null ? agg.color() : refs.containsKey(id) ? refs.get(id).color() : null;
            lines.add(new OverviewResponse.CategoryLine(id, name, color, spent,
                previousSpent.getOrDefault(id, BigDecimal.ZERO),
                budget, budget == null ? null : BudgetStatus.of(spent, budget)));
        }
        lines.sort(Comparator.comparing(OverviewResponse.CategoryLine::spent).reversed()
            .thenComparing(line -> line.name() == null ? "" : line.name(), String.CASE_INSENSITIVE_ORDER));

        BigDecimal totalBudgetAmount = budgets.get(Optional.empty());
        OverviewResponse.TotalBudget totalBudget = totalBudgetAmount == null ? null
            : new OverviewResponse.TotalBudget(totalBudgetAmount, current.expenses(),
                BudgetStatus.of(current.expenses(), totalBudgetAmount));

        List<OverviewResponse.Merchant> merchants = transactions
            .topMerchantsForRange(userId, month.atDay(1), month.atEndOfMonth()).stream()
            .map(row -> new OverviewResponse.Merchant((String) row[0], (BigDecimal) row[1], ((Number) row[2]).longValue()))
            .toList();

        return new OverviewResponse(month.toString(), current.income(), current.expenses(),
            current.income().subtract(current.expenses()), previous.income(), previous.expenses(),
            budgetsEnabled, totalBudget, lines, merchants);
    }

    /** Rows for the Budgets page: the total, then every pickable expense category, each with recent spending. */
    public List<BudgetRowResponse> budgetRows(UUID userId, YearMonth month) {
        MonthTotals current = totals(userId, month);
        MonthTotals previous = totals(userId, month.minusMonths(1));
        Map<UUID, BigDecimal> currentSpent = current.expenseSpentById();
        Map<UUID, BigDecimal> previousSpent = previous.expenseSpentById();
        Map<Optional<UUID>, BigDecimal> budgets = budgetService.budgetsFor(userId, month);

        List<BudgetRowResponse> rows = new ArrayList<>();
        rows.add(new BudgetRowResponse(null, "Total", null,
            budgets.getOrDefault(Optional.empty(), BigDecimal.ZERO), current.expenses(), previous.expenses()));

        categoryService.list(userId, false).stream()
            .filter(c -> c.kind() == CategoryKind.EXPENSE)
            .sorted(Comparator.comparing(CategoryResponse::name, String.CASE_INSENSITIVE_ORDER))
            .forEach(c -> rows.add(new BudgetRowResponse(c.id(), c.name(), c.color(),
                budgets.getOrDefault(Optional.of(c.id()), BigDecimal.ZERO),
                currentSpent.getOrDefault(c.id(), BigDecimal.ZERO),
                previousSpent.getOrDefault(c.id(), BigDecimal.ZERO))));
        return rows;
    }

    private MonthTotals totals(UUID userId, YearMonth month) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;
        Map<UUID, CategoryAgg> expensesById = new LinkedHashMap<>();
        for (Object[] row : transactions.categoryTotalsForRange(userId, month.atDay(1), month.atEndOfMonth())) {
            CategoryKind kind = CategoryKind.valueOf((String) row[COL_KIND]);
            BigDecimal total = (BigDecimal) row[COL_TOTAL];
            if (kind == CategoryKind.EXPENSE) {
                expenses = expenses.add(total);
                expensesById.put((UUID) row[COL_ID],
                    new CategoryAgg((UUID) row[COL_ID], (String) row[COL_NAME], (String) row[COL_COLOR], total));
            } else if (kind == CategoryKind.INCOME) {
                income = income.add(total);
            }
        }
        return new MonthTotals(income, expenses, expensesById);
    }

    private record CategoryAgg(UUID id, String name, String color, BigDecimal total) { }

    private record MonthTotals(BigDecimal income, BigDecimal expenses, Map<UUID, CategoryAgg> expensesById) {
        Map<UUID, BigDecimal> expenseSpentById() {
            Map<UUID, BigDecimal> spent = new LinkedHashMap<>();
            expensesById.forEach((id, agg) -> spent.put(id, agg.total()));
            return spent;
        }
    }
}
