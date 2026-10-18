# ADR-004: Apache Commons CSV for CSV Parsing

## Status
Accepted

## Context
CSV files from different banks have varying:
- Column headers (e.g., "Date" vs "Transaction Date" vs "Post Date")
- Delimiters (comma, semicolon, tab)
- Encoding (UTF-8, ISO-8859-1, Windows-1252)
- Date formats (MM/DD/YYYY, DD/MM/YYYY, YYYY-MM-DD)
- Line endings (CRLF, LF)

CSV parsing happens server-side in the Spring Boot backend. The parser must handle all these variations gracefully and integrate cleanly with Java's type system.

## Decision
Use **Apache Commons CSV** for CSV parsing in the Spring Boot CSV Import Service.

## Consequences

### Positive
- Battle-tested library — part of Apache Commons, widely used in enterprise Java
- Handles all common CSV edge cases (embedded commas, quotes, newlines in fields)
- Builder-pattern API that fits Java idioms:
  ```java
  CSVParser parser = CSVFormat.DEFAULT
      .withFirstRecordAsHeader()
      .withTrim()
      .parse(new InputStreamReader(inputStream));
  ```
- Auto-detects headers — `parser.getHeaderNames()` returns detected columns
- Supports custom delimiter detection via `CSVFormat.Builder`
- Zero dependencies beyond Apache Commons core
- Direct integration with `MultipartFile.getInputStream()`

### Negative
- No built-in encoding detection — must be handled separately or use UTF-8
- No built-in date parsing — must normalize dates after parsing
- No streaming to database — must collect rows into List<DTO> first
- Java-only — no browser-side parsing (not needed with backend-first approach)

### Neutral
- Header mapping logic is custom code in `CsvImportService`, not part of the library
- Same pattern as the previous PapaParse approach, just in Java

## Alternatives Considered

**OpenCSV**
- Considered: Popular alternative with annotation-based mapping (`@CsvBindByName`). Rejected because annotation-based mapping assumes fixed column names, which conflicts with our dynamic header-mapping requirement.

**Super CSV**
- Rejected: Less active maintenance, smaller community.

**Spring Batch FlatFileItemReader**
- Considered: Excellent for large files and batch processing. Overkill for a single-file upload scenario. Adds Spring Batch dependency and complexity.

**PapaParse (JavaScript — previous plan)**
- Rejected for revised stack: JavaScript-only. With backend-first Java architecture, server-side parsing is preferred. PapaParse cannot run in JVM.

## Implementation Sketch

```java
@Service
public class CsvImportService {

    private static final Map<String, String> HEADER_MAPPING = Map.of(
        "date", "date",
        "transaction date", "date",
        "post date", "date",
        "description", "rawDescription",
        "payee", "rawDescription",
        "name", "rawDescription",
        "amount", "amount",
        "debit", "amount",
        "total", "amount"
    );

    public List<ParsedTransactionDTO> parse(MultipartFile file) {
        try (CSVParser parser = CSVFormat.DEFAULT
                .withFirstRecordAsHeader()
                .withTrim()
                .parse(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            Map<String, Integer> headerIndex = parser.getHeaderMap();
            return parser.stream()
                .map(record -> mapToDTO(record, headerIndex))
                .filter(Objects::nonNull)
                .toList();
        }
    }

    private ParsedTransactionDTO mapToDTO(CSVRecord record, Map<String, Integer> headerIndex) {
        // Dynamic mapping using HEADER_MAPPING
        // ...
    }
}
```

## References
- [CSV Ingestion Flow](../README.md#41-csv-ingestion-flow)
- [Apache Commons CSV](https://commons.apache.org/proper/commons-csv/)
