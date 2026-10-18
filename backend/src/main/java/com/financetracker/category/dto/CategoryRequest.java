package com.financetracker.category.dto;

import com.financetracker.category.model.CategoryKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "A new category")
public record CategoryRequest(
    @Schema(example = "Pets") @NotBlank @Size(max = 100) String name,
    @Schema(description = "Defaults to EXPENSE", nullable = true) CategoryKind kind,
    @Schema(example = "#2F80ED", nullable = true) @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color,
    @Schema(nullable = true) @Size(max = 40) String icon
) { }
