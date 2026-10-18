package com.financetracker.budget.service;

import com.financetracker.budget.model.Budget;
import com.financetracker.budget.repository.BudgetRepository;
import com.financetracker.category.event.CategoryArchivedEvent;
import com.financetracker.category.model.CategoryKind;
import com.financetracker.category.model.CategoryRef;
import com.financetracker.category.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Monthly budgets (ADR-019): a standing amount per expense category plus an optional total, each change
 * stored with the month it starts in. Unused money does not roll over.
 */
@Service
@RequiredArgsConstructor
public class BudgetService {

    /** Months are counted in Vietnam time, like receipt dates. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final BudgetRepository budgets;
    private final CategoryService categoryService;
    private Clock clock = Clock.system(ZONE);

    /** For tests. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    public YearMonth currentMonth() {
        return YearMonth.now(clock);
    }

    /**
     * The budgets in force in a month: category id → amount, with the total budget under the key
     * {@link Optional#empty()}. Categories without a budget (or whose budget was ended) are absent.
     */
    public Map<Optional<UUID>, BigDecimal> budgetsFor(UUID userId, YearMonth month) {
        Map<Optional<UUID>, BigDecimal> latest = new HashMap<>();
        for (Budget budget : budgets.findUpTo(userId, month.atDay(1))) {
            latest.put(Optional.ofNullable(budget.getCategoryId()), budget.getAmount());
        }
        latest.values().removeIf(amount -> amount.signum() == 0);
        return latest;
    }

    /**
     * Sets a budget from {@code from} on (that month and later, until changed again). {@code amount} null or 0
     * removes it. {@code categoryId} null is the total budget; otherwise it must be one of the user's pickable
     * expense categories.
     */
    @Transactional
    public void set(UUID userId, UUID categoryId, BigDecimal amount, YearMonth from) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        if (value.signum() < 0) {
            throw new IllegalArgumentException("A budget can't be negative");
        }
        if (categoryId != null) {
            CategoryRef category = categoryService.usableRef(userId, categoryId);
            if (category.kind() != CategoryKind.EXPENSE) {
                throw new IllegalArgumentException("Budgets are for spending categories; '" + category.name() + "' isn't one");
            }
        }
        Budget row = budgets.findExact(userId, categoryId, from.atDay(1))
            .orElseGet(() -> Budget.builder().userId(userId).categoryId(categoryId).effectiveFrom(from.atDay(1)).build());
        row.setAmount(value);
        budgets.save(row);
    }

    /** An archived category's budget ends from next month; this month and earlier keep it. */
    @EventListener
    @Transactional
    public void onCategoryArchived(CategoryArchivedEvent event) {
        YearMonth next = currentMonth().plusMonths(1);
        List<UUID> owners = event.ownerId() != null ? List.of(event.ownerId()) : budgets.findUsersWithBudgetOn(event.categoryId());
        for (UUID owner : owners) {
            if (budgetsFor(owner, next).containsKey(Optional.of(event.categoryId()))) {
                Budget row = budgets.findExact(owner, event.categoryId(), next.atDay(1))
                    .orElseGet(() -> Budget.builder().userId(owner).categoryId(event.categoryId()).effectiveFrom(next.atDay(1)).build());
                row.setAmount(BigDecimal.ZERO);
                budgets.save(row);
            }
        }
    }
}
