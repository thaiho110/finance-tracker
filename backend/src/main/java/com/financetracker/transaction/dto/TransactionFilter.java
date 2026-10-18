package com.financetracker.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.UUID;

/** Optional filters for listing transactions, bound from query parameters. */
public record TransactionFilter(
    @Schema(description = "Only this category", nullable = true) UUID categoryId,
    @Schema(description = "Only this category, by name (older clients); ignored when categoryId is set", nullable = true)
    String category,
    @Schema(description = "From this date, inclusive", nullable = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate dateFrom,
    @Schema(description = "Up to this date, inclusive", nullable = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate dateTo
) { }
