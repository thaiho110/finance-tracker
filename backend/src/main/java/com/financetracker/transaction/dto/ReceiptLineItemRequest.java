package com.financetracker.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "A line item from a scanned receipt")
public record ReceiptLineItemRequest(
    @Schema(description = "Item description as printed on the receipt", example = "RAU MUONG")
    @NotBlank @Size(max = 255) String item,

    @Schema(description = "Line price in VND", example = "12000")
    @NotNull @PositiveOrZero BigDecimal price
) { }
