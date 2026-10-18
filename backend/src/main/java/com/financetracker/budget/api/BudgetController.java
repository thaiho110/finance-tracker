package com.financetracker.budget.api;

import com.financetracker.auth.security.AuthenticatedUser;
import com.financetracker.auth.security.CurrentUser;
import com.financetracker.budget.dto.BudgetRowResponse;
import com.financetracker.budget.dto.BudgetSetRequest;
import com.financetracker.budget.service.BudgetService;
import com.financetracker.overview.service.OverviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

/**
 * Editing monthly budgets (ADR-019). Needs the BUDGETS feature. A budget change takes effect from a month
 * on and leaves past months alone; amount 0 (or null) ends it.
 */
@RestController
@RequestMapping("/api/v1/budgets")
@RequiredArgsConstructor
@Tag(name = "Budgets", description = "Monthly budgets per expense category, plus an optional total")
public class BudgetController {

    private final BudgetService budgetService;
    private final OverviewService overviewService;

    @GetMapping
    @Operation(summary = "Every pickable expense category and the total, with the budget and spending for a month")
    public ResponseEntity<List<BudgetRowResponse>> list(@RequestParam(required = false) String month,
                                                        @CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(overviewService.budgetRows(user.getId(), overviewService.parseMonth(month)));
    }

    @PutMapping
    @Operation(summary = "Set or clear a budget from a month on; returns the rows for that month")
    public ResponseEntity<List<BudgetRowResponse>> set(@Valid @RequestBody BudgetSetRequest request,
                                                      @CurrentUser AuthenticatedUser user) {
        YearMonth month = overviewService.parseMonth(request.month());
        budgetService.set(user.getId(), request.categoryId(), request.amount(), month);
        return ResponseEntity.ok(overviewService.budgetRows(user.getId(), month));
    }
}
