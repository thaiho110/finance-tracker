package com.financetracker.access.api;

import com.financetracker.access.dto.SetModuleRequest;
import com.financetracker.access.model.Module;
import com.financetracker.access.model.ModuleState;
import com.financetracker.access.service.AccessService;
import com.financetracker.auth.security.AuthenticatedUser;
import com.financetracker.auth.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The caller's own modules (ADR-020 §4): which parts of Kept they have turned on. Always on (no permission) —
 * a user manages their own modules. Turning a module off only narrows what they can do; at least one available
 * module must stay on.
 */
@RestController
@RequestMapping("/api/v1/modules")
@RequiredArgsConstructor
@Tag(name = "Modules", description = "The user's own modules (what parts of Kept they use)")
public class ModuleController {

    private final AccessService accessService;

    @GetMapping
    @Operation(summary = "The caller's modules, each with its enabled and available state")
    public ResponseEntity<List<ModuleState>> list(@CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(accessService.modules(user.getId(), user.getRoleCode()));
    }

    @PutMapping("/{code}")
    @Operation(summary = "Turn a module on or off for the caller; returns the updated module states")
    public ResponseEntity<List<ModuleState>> set(@PathVariable String code,
                                                 @Valid @RequestBody SetModuleRequest request,
                                                 @CurrentUser AuthenticatedUser user) {
        accessService.setModule(user.getId(), user.getRoleCode(), Module.from(code), request.enabled());
        return ResponseEntity.ok(accessService.modules(user.getId(), user.getRoleCode()));
    }
}
