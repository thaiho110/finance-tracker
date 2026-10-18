package com.financetracker.csv.service;

import com.financetracker.common.dto.ParsedTransactionResponse;
import com.financetracker.transaction.intake.DraftTransaction;
import com.financetracker.transaction.intake.LocalValues;
import com.financetracker.transaction.intake.RawEntry;
import com.financetracker.transaction.intake.TransactionIntake;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * CSV adapter for {@link TransactionIntake}: finds the date, description and amount columns of a bank
 * statement, reads each row into a {@link RawEntry}, and lets the intake categorize and check duplicates.
 * Cells that can't be read become null so the review grid can ask the user to fill them in.
 */
@Service
@RequiredArgsConstructor
public class CsvImportService {

    /** Accepted header names (compared in lower case), in order of preference. */
    private static final List<String> DATE_HEADERS = List.of(
        "date", "transaction date", "post date", "posting date", "ngày", "ngày giao dịch", "ngay giao dich");
    private static final List<String> DESCRIPTION_HEADERS = List.of(
        "description", "payee", "name", "memo", "merchant", "mô tả", "nội dung", "noi dung", "diễn giải");
    private static final List<String> AMOUNT_HEADERS = List.of(
        "amount", "debit", "credit", "total", "value", "số tiền", "so tien", "ghi nợ", "ghi có");

    private static final String BYTE_ORDER_MARK = "\uFEFF";

    private final TransactionIntake intake;

    public List<ParsedTransactionResponse> parse(MultipartFile file, UUID userId) {
        List<DraftTransaction> drafts = intake.draft(userId, readEntries(file));
        List<ParsedTransactionResponse> rows = new ArrayList<>(drafts.size());
        for (int rowIndex = 0; rowIndex < drafts.size(); rowIndex++) {
            DraftTransaction draft = drafts.get(rowIndex);
            rows.add(new ParsedTransactionResponse(rowIndex, draft.date(), draft.rawDescription(),
                draft.cleanMerchant(), draft.amount(), draft.category(), draft.duplicate()));
        }
        return rows;
    }

    private List<RawEntry> readEntries(MultipartFile file) {
        try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT
                 .withFirstRecordAsHeader()
                 .withTrim()
                 .parse(reader)) {
            Map<String, Integer> headers = normalizeHeaders(parser.getHeaderMap());
            List<Integer> dateColumns = columns(headers, DATE_HEADERS);
            List<Integer> descriptionColumns = columns(headers, DESCRIPTION_HEADERS);
            List<Integer> amountColumns = columns(headers, AMOUNT_HEADERS);
            List<RawEntry> entries = new ArrayList<>();
            for (CSVRecord row : parser) {
                entries.add(new RawEntry(
                    LocalValues.parseDate(firstValue(row, dateColumns)),
                    firstValue(row, descriptionColumns),
                    LocalValues.parseAmount(firstValue(row, amountColumns))));
            }
            return entries;
        } catch (IOException | UncheckedIOException | IllegalStateException e) {
            throw new IllegalArgumentException("Failed to parse CSV: " + e.getMessage(), e);
        }
    }

    /** Header name (lower case, no byte-order mark) → column index. */
    private static Map<String, Integer> normalizeHeaders(Map<String, Integer> headerMap) {
        Map<String, Integer> headers = new HashMap<>();
        if (headerMap != null) {
            headerMap.forEach((name, index) ->
                headers.putIfAbsent(name.replace(BYTE_ORDER_MARK, "").strip().toLowerCase(Locale.ROOT), index));
        }
        return headers;
    }

    /** Indexes of the columns present in this file for one field, in preference order. */
    private static List<Integer> columns(Map<String, Integer> headers, List<String> names) {
        return names.stream().filter(headers::containsKey).map(headers::get).toList();
    }

    /** The first non-blank cell among {@code columns}, or "" when there is none. */
    private static String firstValue(CSVRecord row, List<Integer> columns) {
        return columns.stream()
            .filter(column -> column < row.size())
            .map(row::get)
            .filter(value -> value != null && !value.isBlank())
            .map(String::strip)
            .findFirst()
            .orElse("");
    }
}
