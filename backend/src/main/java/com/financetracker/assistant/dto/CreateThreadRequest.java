package com.financetracker.assistant.dto;

import jakarta.validation.constraints.Size;

/** Start an assistant thread (ADR-024). The title is optional. */
public record CreateThreadRequest(@Size(max = 200) String title) { }
