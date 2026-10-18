package com.financetracker.admin.api;

import com.financetracker.admin.dto.CreateInviteResponse;
import com.financetracker.admin.dto.InviteResponse;
import com.financetracker.admin.dto.RegistrationModeResponse;
import com.financetracker.admin.dto.UpdateRegistrationModeRequest;
import com.financetracker.admin.model.AuditAction;
import com.financetracker.admin.service.AuditService;
import com.financetracker.auth.model.RegistrationMode;
import com.financetracker.auth.security.AuthenticatedUser;
import com.financetracker.auth.security.CurrentUser;
import com.financetracker.auth.service.InviteService;
import com.financetracker.settings.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Registration mode and single-use invites (ADR-016). Gating is set per URL in {@code SecurityConfig}. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Administration", description = "Registration mode and invite links")
public class RegistrationAdminController {

    private final SettingsService settingsService;
    private final InviteService inviteService;
    private final AuditService auditService;

    @GetMapping("/registration")
    @Operation(summary = "The current registration mode (USER_READ)")
    public ResponseEntity<RegistrationModeResponse> getMode() {
        return ResponseEntity.ok(new RegistrationModeResponse(settingsService.getRegistrationMode()));
    }

    @PutMapping("/registration")
    @Operation(summary = "Change the registration mode (ROLE_MANAGE)")
    public ResponseEntity<RegistrationModeResponse> setMode(@Valid @RequestBody UpdateRegistrationModeRequest request,
                                                            @CurrentUser AuthenticatedUser actor) {
        RegistrationMode previous = settingsService.getRegistrationMode();
        settingsService.setRegistrationMode(request.mode());
        auditService.record(actor.getId(), AuditAction.REGISTRATION_MODE_CHANGED, "registration",
            previous + " -> " + request.mode());
        return ResponseEntity.ok(new RegistrationModeResponse(request.mode()));
    }

    @GetMapping("/invites")
    @Operation(summary = "List invites (without their tokens) (USER_READ)")
    public ResponseEntity<List<InviteResponse>> listInvites() {
        return ResponseEntity.ok(inviteService.list().stream().map(InviteResponse::of).toList());
    }

    @PostMapping("/invites")
    @Operation(summary = "Create a single-use invite; the token is shown once (USER_CREATE)")
    public ResponseEntity<CreateInviteResponse> createInvite(@CurrentUser AuthenticatedUser actor) {
        InviteService.Created created = inviteService.create(actor.getId());
        auditService.record(actor.getId(), AuditAction.INVITE_CREATED, "invite", created.id().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(
            new CreateInviteResponse(created.id(), created.token(), created.expiresAt()));
    }

    @DeleteMapping("/invites/{id}")
    @Operation(summary = "Revoke an invite so its link stops working (USER_CREATE)")
    public ResponseEntity<Void> revokeInvite(@PathVariable UUID id, @CurrentUser AuthenticatedUser actor) {
        inviteService.revoke(id);
        auditService.record(actor.getId(), AuditAction.INVITE_REVOKED, "invite", id.toString());
        return ResponseEntity.noContent().build();
    }
}
