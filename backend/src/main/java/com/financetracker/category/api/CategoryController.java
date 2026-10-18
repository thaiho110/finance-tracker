package com.financetracker.category.api;

import com.financetracker.auth.security.AuthenticatedUser;
import com.financetracker.auth.security.CurrentUser;
import com.financetracker.category.dto.CategoryDeleteResponse;
import com.financetracker.category.dto.CategoryRequest;
import com.financetracker.category.dto.CategoryResponse;
import com.financetracker.category.dto.CategoryRulePreviewResponse;
import com.financetracker.category.dto.CategoryRuleRequest;
import com.financetracker.category.dto.CategoryRuleResponse;
import com.financetracker.category.dto.CategoryRuleSaveResponse;
import com.financetracker.category.dto.CategoryUpdateRequest;
import com.financetracker.category.service.CategoryService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The signed-in user's categories and keyword rules (ADR-015). Reads need CATEGORY_READ, changes CATEGORY_WRITE. */
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Default and own categories, and keyword rules that pick them")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "Categories the user can pick; includeArchived=true also returns archived and hidden ones")
    public ResponseEntity<List<CategoryResponse>> list(
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(categoryService.list(user.getId(), includeArchived));
    }

    @PostMapping
    @Operation(summary = "Create one of the user's own categories")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request,
                                                   @CurrentUser AuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(user.getId(), request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Rename, recolor, change kind or archive one of the user's own categories")
    public ResponseEntity<CategoryResponse> update(@PathVariable UUID id,
                                                   @Valid @RequestBody CategoryUpdateRequest request,
                                                   @CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(categoryService.update(user.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an unused category, or archive a used one; moveTo moves its transactions first")
    public ResponseEntity<CategoryDeleteResponse> delete(@PathVariable UUID id,
                                                         @RequestParam(required = false) UUID moveTo,
                                                         @CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(categoryService.delete(user.getId(), id, moveTo));
    }

    @PostMapping("/{id}/hide")
    @Operation(summary = "Hide a default category for this user")
    public ResponseEntity<Void> hide(@PathVariable UUID id, @CurrentUser AuthenticatedUser user) {
        categoryService.setHidden(user.getId(), id, true);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/hide")
    @Operation(summary = "Show a hidden default category again")
    public ResponseEntity<Void> unhide(@PathVariable UUID id, @CurrentUser AuthenticatedUser user) {
        categoryService.setHidden(user.getId(), id, false);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/rules")
    @Operation(summary = "The user's own rules; scope=system lists the read-only defaults")
    public ResponseEntity<List<CategoryRuleResponse>> rules(@RequestParam(defaultValue = "own") String scope,
                                                            @CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(categoryService.listRules(user.getId(), "system".equalsIgnoreCase(scope)));
    }

    @GetMapping("/rules/preview")
    @Operation(summary = "How many past transactions still in Other a keyword would move")
    public ResponseEntity<CategoryRulePreviewResponse> preview(@RequestParam String keyword,
                                                               @CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(categoryService.preview(user.getId(), keyword));
    }

    @PostMapping("/rules")
    @Operation(summary = "Create or replace the user's rule for a keyword (\"Always use Coffee for HIGHLANDS\")")
    public ResponseEntity<CategoryRuleSaveResponse> saveRule(@Valid @RequestBody CategoryRuleRequest request,
                                                             @CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(categoryService.saveRule(user.getId(), request));
    }

    @DeleteMapping("/rules/{id}")
    @Operation(summary = "Delete one of the user's rules")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id, @CurrentUser AuthenticatedUser user) {
        categoryService.deleteRule(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
