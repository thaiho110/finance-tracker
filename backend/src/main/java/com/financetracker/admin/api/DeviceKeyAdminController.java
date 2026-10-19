package com.financetracker.admin.api;

import com.financetracker.admin.dto.CreateDeviceKeyRequest;
import com.financetracker.admin.dto.CreateDeviceKeyResponse;
import com.financetracker.admin.dto.DeviceKeyResponse;
import com.financetracker.admin.model.AuditAction;
import com.financetracker.admin.service.AuditService;
import com.financetracker.auth.security.AuthenticatedUser;
import com.financetracker.auth.security.CurrentUser;
import com.financetracker.auth.service.DeviceApiKeyService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Device API keys (ADR-016). Gating is set per URL in {@code SecurityConfig}. */
@RestController
@RequestMapping("/api/v1/admin/device-keys")
@RequiredArgsConstructor
@Tag(name = "Administration", description = "Device API keys")
public class DeviceKeyAdminController {

    private final DeviceApiKeyService deviceApiKeyService;
    private final AuditService auditService;

    @GetMapping
    @Operation(summary = "List device keys (without their raw keys) (USER_READ)")
    public ResponseEntity<List<DeviceKeyResponse>> list() {
        return ResponseEntity.ok(deviceApiKeyService.list().stream().map(DeviceKeyResponse::of).toList());
    }

    @PostMapping
    @Operation(summary = "Create a device key; the key is shown once (USER_CREATE)")
    public ResponseEntity<CreateDeviceKeyResponse> create(@Valid @RequestBody CreateDeviceKeyRequest request,
                                                          @CurrentUser AuthenticatedUser actor) {
        DeviceApiKeyService.Created created = deviceApiKeyService.create(request.userId(), request.label(), actor.getId());
        auditService.record(actor.getId(), AuditAction.DEVICE_KEY_CREATED, "device-key",
            created.id() + " for " + created.ownerEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(CreateDeviceKeyResponse.of(created));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Revoke a device key so it stops authenticating (USER_CREATE)")
    public ResponseEntity<Void> revoke(@PathVariable UUID id, @CurrentUser AuthenticatedUser actor) {
        deviceApiKeyService.revoke(id);
        auditService.record(actor.getId(), AuditAction.DEVICE_KEY_REVOKED, "device-key", id.toString());
        return ResponseEntity.noContent().build();
    }
}
