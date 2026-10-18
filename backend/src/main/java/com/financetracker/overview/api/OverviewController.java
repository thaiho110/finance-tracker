package com.financetracker.overview.api;

import com.financetracker.access.model.Permission;
import com.financetracker.auth.security.AuthenticatedUser;
import com.financetracker.auth.security.CurrentUser;
import com.financetracker.overview.dto.OverviewResponse;
import com.financetracker.overview.service.OverviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The monthly overview (ADR-019): income, spending, net, per-category spending vs budget and vs last month,
 * and top merchants. Needs DASHBOARD_VIEW; budget fields are filled only when the user also has BUDGETS.
 */
@RestController
@RequestMapping("/api/v1/overview")
@RequiredArgsConstructor
@Tag(name = "Overview", description = "A month's income, spending, budgets and top merchants")
public class OverviewController {

    private final OverviewService overviewService;

    @GetMapping
    @Operation(summary = "One month's overview; month defaults to the current month (Asia/Ho_Chi_Minh)")
    public ResponseEntity<OverviewResponse> overview(@RequestParam(required = false) String month,
                                                     @CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(overviewService.overview(user.getId(),
            overviewService.parseMonth(month), user.hasPermission(Permission.BUDGETS)));
    }
}
