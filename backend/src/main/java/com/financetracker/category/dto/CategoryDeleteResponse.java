package com.financetracker.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "What removing a category did")
public record CategoryDeleteResponse(
    @Schema(description = "DELETED when nothing used it, otherwise ARCHIVED") Outcome outcome,
    @Schema(description = "Transactions moved to the replacement category") int moved
) {
    public enum Outcome { DELETED, ARCHIVED }
}
