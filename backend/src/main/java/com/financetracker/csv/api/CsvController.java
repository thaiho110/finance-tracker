package com.financetracker.csv.api;

import com.financetracker.common.dto.ParsedTransactionResponse;
import com.financetracker.csv.service.CsvImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/csv")
@RequiredArgsConstructor
@Tag(name = "CSV Import", description = "Upload and parse bank CSV statements")
public class CsvController {

    private final CsvImportService csvImportService;

    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload and parse a CSV bank statement")
    public ResponseEntity<List<ParsedTransactionResponse>> parseCsv(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is empty");
        }

        UUID userId = UUID.nameUUIDFromBytes(userDetails.getUsername().getBytes());
        return ResponseEntity.ok(csvImportService.parse(file, userId));
    }
}
