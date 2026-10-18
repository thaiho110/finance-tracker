package com.financetracker.category.api;

import com.financetracker.admin.model.AuditAction;
import com.financetracker.admin.service.AuditService;
import com.financetracker.auth.security.AuthenticatedUser;
import com.financetracker.auth.security.CurrentUser;
import com.financetracker.category.dto.CategoryRequest;
import com.financetracker.category.dto.CategoryResponse;
import com.financetracker.category.dto.CategoryRuleRequest;
import com.financetracker.category.dto.CategoryRuleResponse;
import com.financetracker.category.dto.CategoryUpdateRequest;
import com.financetracker.category.service.SystemCategoryService;
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

/** Default categories and rules shared by every user (CATEGORY_ADMIN). Every change is audited. */
@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
@Tag(name = "Administration", description = "Accounts, roles, per-user features and the audit log")
public class AdminCategoryController {

    private final SystemCategoryService systemCategories;
    private final AuditService audit;

    @GetMapping
    @Operation(summary = "Default categories, archived included (CATEGORY_ADMIN)")
    public ResponseEntity<List<CategoryResponse>> list() {
        return ResponseEntity.ok(systemCategories.list());
    }

    @PostMapping
    @Operation(summary = "Add a default category (CATEGORY_ADMIN)")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request,
                                                   @CurrentUser AuthenticatedUser admin) {
        CategoryResponse created = systemCategories.create(request);
        audit.record(admin.getId(), AuditAction.SYSTEM_CATEGORY_CREATED, created.name(), created.kind().name());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Rename, recolor or archive a default category (CATEGORY_ADMIN)")
    public ResponseEntity<CategoryResponse> update(@PathVariable UUID id,
                                                   @Valid @RequestBody CategoryUpdateRequest request,
                                                   @CurrentUser AuthenticatedUser admin) {
        CategoryResponse updated = systemCategories.update(id, request);
        audit.record(admin.getId(), AuditAction.SYSTEM_CATEGORY_CHANGED, updated.name(), describe(request));
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/rules")
    @Operation(summary = "Default keyword rules (CATEGORY_ADMIN)")
    public ResponseEntity<List<CategoryRuleResponse>> rules() {
        return ResponseEntity.ok(systemCategories.listRules());
    }

    @PostMapping("/rules")
    @Operation(summary = "Create or replace a default keyword rule (CATEGORY_ADMIN)")
    public ResponseEntity<CategoryRuleResponse> saveRule(@Valid @RequestBody CategoryRuleRequest request,
                                                         @CurrentUser AuthenticatedUser admin) {
        CategoryRuleResponse saved = systemCategories.saveRule(request.keyword(), request.categoryId());
        audit.record(admin.getId(), AuditAction.SYSTEM_RULE_SAVED, saved.keyword(), saved.categoryName());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/rules/{id}")
    @Operation(summary = "Delete a default keyword rule (CATEGORY_ADMIN)")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id, @CurrentUser AuthenticatedUser admin) {
        String keyword = systemCategories.deleteRule(id);
        audit.record(admin.getId(), AuditAction.SYSTEM_RULE_DELETED, keyword, null);
        return ResponseEntity.noContent().build();
    }

    private static String describe(CategoryUpdateRequest request) {
        StringBuilder changes = new StringBuilder();
        if (request.name() != null) {
            changes.append("name ");
        }
        if (request.kind() != null) {
            changes.append("kind=").append(request.kind()).append(' ');
        }
        if (request.color() != null) {
            changes.append("color ");
        }
        if (request.archived() != null) {
            changes.append(request.archived() ? "archived" : "restored");
        }
        return changes.toString().strip();
    }
}
