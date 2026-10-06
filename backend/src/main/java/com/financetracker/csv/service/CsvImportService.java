package com.financetracker.csv.service;

import com.financetracker.category.service.CategorizationService;
import com.financetracker.common.dto.ParsedTransactionResponse;
import com.financetracker.transaction.service.DeduplicationService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CsvImportService {

    private static final Map<String, String> HEADER_MAPPING = new HashMap<>();
    static {
        HEADER_MAPPING.put("date", "date");
        HEADER_MAPPING.put("transaction date", "date");
        HEADER_MAPPING.put("post date", "date");
        HEADER_MAPPING.put("description", "rawDescription");
        HEADER_MAPPING.put("payee", "rawDescription");
        HEADER_MAPPING.put("name", "rawDescription");
        HEADER_MAPPING.put("memo", "rawDescription");
        HEADER_MAPPING.put("amount", "amount");
        HEADER_MAPPING.put("debit", "amount");
        HEADER_MAPPING.put("credit", "amount");
        HEADER_MAPPING.put("total", "amount");
        HEADER_MAPPING.put("value", "amount");
    }

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("M/d/yyyy"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("MM-dd-yyyy")
    );

    private final CategorizationService categorizationService;
    private final DeduplicationService deduplicationService;

    public List<ParsedTransactionResponse> parse(MultipartFile file, UUID userId) {
        List<ParsedTransactionResponse> results = new ArrayList<>();

        try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT
                 .withFirstRecordAsHeader()
                 .withTrim()
                 .parse(reader)) {

            Map<String, Integer> headerIndex = parser.getHeaderMap();
            int rowIndex = 0;

            for (CSVRecord record : parser) {
                try {
                    ParsedTransactionResponse parsed = mapToResponse(record, headerIndex, userId, rowIndex);
                    results.add(parsed);
                } catch (Exception e) {
                    // Skip malformed rows
                    results.add(new ParsedTransactionResponse(
                        rowIndex, null, record.toString(), "PARSE_ERROR",
                        BigDecimal.ZERO, "Other", false));
                }
                rowIndex++;
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse CSV: " + e.getMessage());
        }

        return results;
    }

    private ParsedTransactionResponse mapToResponse(CSVRecord record, Map<String, Integer> headerIndex,
                                                     UUID userId, int rowIndex) {
        String rawDescription = getField(record, headerIndex, "rawDescription", "");
        String cleanMerchant = categorizationService.cleanMerchant(rawDescription);
        String category = categorizationService.categorize(cleanMerchant);
        LocalDate date = parseDate(getField(record, headerIndex, "date", ""));
        BigDecimal amount = parseAmount(getField(record, headerIndex, "amount", "0"));
        boolean isDuplicate = deduplicationService.isDuplicate(userId, date, amount);

        return new ParsedTransactionResponse(rowIndex, date, rawDescription, cleanMerchant, amount, category, isDuplicate);
    }

    private String getField(CSVRecord record, Map<String, Integer> headerIndex, String canonicalName, String defaultValue) {
        for (Map.Entry<String, String> entry : HEADER_MAPPING.entrySet()) {
            if (entry.getValue().equals(canonicalName)) {
                Integer idx = headerIndex.get(entry.getKey());
                if (idx != null) {
                    String value = record.get(idx);
                    if (value != null && !value.isBlank()) return value.trim();
                }
            }
        }
        return defaultValue;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return LocalDate.now();
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(value.trim(), fmt);
            } catch (DateTimeParseException ignored) {}
        }
        return LocalDate.now();
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value.replaceAll("[^\\d.-]", ""));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
