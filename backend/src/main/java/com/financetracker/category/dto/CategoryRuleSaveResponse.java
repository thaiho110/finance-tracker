package com.financetracker.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The saved rule and how many past transactions it moved")
public record CategoryRuleSaveResponse(CategoryRuleResponse rule, int moved) { }
