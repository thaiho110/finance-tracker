package com.financetracker.category.dto;

import com.financetracker.category.model.Category;
import com.financetracker.category.model.CategoryKind;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "A category the user can pick")
public record CategoryResponse(
    UUID id,
    @Schema(example = "Coffee Shop") String name,
    @Schema(description = "EXPENSE, INCOME or TRANSFER (transfers count in no total)") CategoryKind kind,
    @Schema(example = "#8B5E3C", nullable = true) String color,
    @Schema(nullable = true) String icon,
    @Schema(description = "A default shared by everyone; only admins change it") boolean system,
    @Schema(description = "Hidden from pickers and rules, still shown on old transactions") boolean archived,
    @Schema(description = "A system category this user chose to hide") boolean hidden
) {
    public static CategoryResponse of(Category category, boolean hidden) {
        return new CategoryResponse(category.getId(), category.getName(), category.getKind(), category.getColor(),
            category.getIcon(), category.isSystem(), category.isArchived(), hidden);
    }
}
