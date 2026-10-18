package com.financetracker.category.dto;

import com.financetracker.category.model.CategoryKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Every field is optional; a null field is left unchanged. */
@Schema(description = "Changes to a category; null fields stay as they are")
public record CategoryUpdateRequest(
    @Schema(nullable = true) @Size(min = 1, max = 100) String name,
    @Schema(nullable = true) CategoryKind kind,
    @Schema(nullable = true) @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color,
    @Schema(nullable = true) @Size(max = 40) String icon,
    @Schema(nullable = true) Boolean archived
) { }
