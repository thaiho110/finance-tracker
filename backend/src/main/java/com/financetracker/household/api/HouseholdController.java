package com.financetracker.household.api;

import com.financetracker.auth.security.AuthenticatedUser;
import com.financetracker.auth.security.CurrentUser;
import com.financetracker.household.dto.CreateHouseholdRequest;
import com.financetracker.household.dto.HouseholdView;
import com.financetracker.household.dto.InviteRequest;
import com.financetracker.household.dto.InviteResponse;
import com.financetracker.household.dto.JoinRequest;
import com.financetracker.household.model.HouseholdInvite;
import com.financetracker.household.service.HouseholdService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Households (ADR-022). Always on (no module), user-scoped. A user is in at most one household; these
 * endpoints create one, invite others, accept an invite, and leave.
 */
@RestController
@RequestMapping("/api/v1/household")
@RequiredArgsConstructor
@Tag(name = "Household", description = "The user's household: membership and invites")
public class HouseholdController {

    private final HouseholdService householdService;

    @GetMapping
    @Operation(summary = "The caller's household and members; 204 if they are not in one")
    public ResponseEntity<HouseholdView> current(@CurrentUser AuthenticatedUser user) {
        return householdService.viewOf(user.getId())
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    @Operation(summary = "Create a household and join it as the first member")
    public ResponseEntity<HouseholdView> create(@Valid @RequestBody CreateHouseholdRequest request,
                                                @CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(householdService.create(user.getId(), request.name()));
    }

    @PostMapping("/invites")
    @Operation(summary = "Create a shareable invite code to the caller's household")
    public ResponseEntity<InviteResponse> invite(@Valid @RequestBody InviteRequest request,
                                                 @CurrentUser AuthenticatedUser user) {
        HouseholdInvite invite = householdService.invite(user.getId(), request.email());
        return ResponseEntity.ok(new InviteResponse(invite.getCode(), invite.getExpiresAt()));
    }

    @PostMapping("/join")
    @Operation(summary = "Accept an invite by its code and join that household")
    public ResponseEntity<HouseholdView> join(@Valid @RequestBody JoinRequest request,
                                             @CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(householdService.join(user.getId(), request.code()));
    }

    @DeleteMapping("/members/me")
    @Operation(summary = "Leave the caller's household; deletes it if now empty")
    public ResponseEntity<Void> leave(@CurrentUser AuthenticatedUser user) {
        householdService.leave(user.getId());
        return ResponseEntity.noContent().build();
    }
}
