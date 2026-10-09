package com.financetracker.transaction.api;

import com.financetracker.transaction.dto.TransactionRequest;
import com.financetracker.transaction.dto.TransactionResponse;
import com.financetracker.transaction.dto.TransactionSummaryResponse;
import com.financetracker.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "CRUD operations for financial transactions")
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/batch")
    @Operation(summary = "Save multiple confirmed transactions")
    public ResponseEntity<List<TransactionResponse>> batchCreate(
            @Valid @RequestBody List<TransactionRequest> requests,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request) {
        UUID userId = UUID.nameUUIDFromBytes(userDetails.getUsername().getBytes(StandardCharsets.UTF_8));
        String clientId = extractClientId(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(transactionService.batchCreate(requests, userId, clientId));
    }

    @GetMapping
    @Operation(summary = "List transactions with pagination and filtering")
    public ResponseEntity<Page<TransactionResponse>> listTransactions(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @PageableDefault(size = 20, sort = "date", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.nameUUIDFromBytes(userDetails.getUsername().getBytes(StandardCharsets.UTF_8));
        return ResponseEntity.ok(transactionService.findAll(userId, category, dateFrom, dateTo, pageable));
    }

    @GetMapping("/summary")
    @Operation(summary = "Get dashboard summary with category breakdown, monthly trend, and recent transactions")
    public ResponseEntity<TransactionSummaryResponse> getSummary(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.nameUUIDFromBytes(userDetails.getUsername().getBytes(StandardCharsets.UTF_8));
        return ResponseEntity.ok(transactionService.getSummary(userId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single transaction by ID")
    public ResponseEntity<TransactionResponse> getTransaction(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.nameUUIDFromBytes(userDetails.getUsername().getBytes(StandardCharsets.UTF_8));
        return ResponseEntity.ok(transactionService.findById(id, userId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing transaction")
    public ResponseEntity<TransactionResponse> updateTransaction(
            @PathVariable UUID id,
            @Valid @RequestBody TransactionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.nameUUIDFromBytes(userDetails.getUsername().getBytes(StandardCharsets.UTF_8));
        return ResponseEntity.ok(transactionService.update(id, request, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a transaction")
    public ResponseEntity<Void> deleteTransaction(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.nameUUIDFromBytes(userDetails.getUsername().getBytes(StandardCharsets.UTF_8));
        transactionService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    private String extractClientId(HttpServletRequest request) {
        String clientId = (String) request.getAttribute("X-Client-Id");
        return clientId != null ? clientId : "unknown";
    }
}
