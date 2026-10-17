# ADR-014: Local Tesseract OCR + Rule-Based Receipt Parsing

**Status:** Accepted  
**Date:** 2026-10-18  
**Deciders:** Thai Ho  
**Supersedes:** [ADR-003](./003-vision-api-ocr.md) (Vision API for Receipt OCR)

## Context

ADR-003 chose the OpenAI Vision API (`gpt-4o-mini`) to turn receipt photos into structured data. It was never wired in: `OcrService.extractReceiptData()` is still a stub that returns `UNKNOWN MERCHANT`, today's date and `0`. The surrounding pipeline is real and stays: PDF rendering, image normalization, deduplication and categorization.

Before implementing it, the Vision API approach was re-evaluated against the product principles:

- **Privacy by default** (`PRODUCT.md` principle 3: "no third-party data sharing"). Sending every receipt photo to OpenAI contradicts this.
- **Self-hosted, no cloud dependency.** The app runs on Docker Compose. A per-request external API adds an internet requirement, an API key to manage, rate limits and a vendor that can change pricing or retire the model.
- **Cost is not actually negligible.** ADR-003 estimated ~500 tokens per receipt, but images are billed at many more tokens than text, so the real cost is higher than estimated (still small, but recurring and unbounded without caps).
- **The user always reviews before saving** (principle 2: "preview before persist"). OCR only has to *pre-fill* an editable form, not be perfect.
- **Receipts are a secondary input.** Bank CSV imports already cover card and transfer payments. Receipts mainly matter for cash purchases.

Receipts in scope are Vietnamese retail and F&B receipts: mostly thermal paper, uppercase text often without diacritics, amounts written as `184.500` (dot = thousands separator), dates as `dd/MM/yyyy`.

## Decision

Replace the planned Vision API call with **Tesseract OCR running locally in the backend container** (via **Tess4J**), followed by **image preprocessing** before OCR and **rule-based parsing** after it. No receipt data leaves the server.

> Tesseract 4+ uses an LSTM neural network internally, but it is an open-source library executed locally, with no API calls, keys or per-request cost.

### Pipeline

```
upload (jpg/png/pdf)
  → [client] downscale + re-encode to JPEG, apply EXIF rotation
  → [OcrService] validate, PDF → image (existing), decode
  → [ReceiptImagePreprocessor] grayscale → upscale → denoise → adaptive threshold → deskew
  → [TesseractReceiptExtractor] Tesseract (vie+eng, PSM 4) → lines of text
  → [ReceiptTextParser] rules → merchant, date, total, line items, warnings
  → [OcrService] categorize + dedup (existing) → ParsedReceiptResponse
  → image bytes discarded (existing behaviour)
```

### 1. Extraction behind an interface

```java
public interface ReceiptExtractor {
    ExtractedReceipt extract(BufferedImage image);
}

public record ExtractedReceipt(
    @Nullable String merchant,
    @Nullable LocalDate date,
    @Nullable BigDecimal total,
    List<LineItem> lineItems,
    List<String> warnings,      // e.g. TOTAL_NOT_FOUND, ITEMS_DO_NOT_MATCH_TOTAL
    String rawText              // kept in memory only, never logged or stored
) { }
```

- `TesseractReceiptExtractor` is the production implementation.
- A `FakeReceiptExtractor` is used in unit tests and CI.
- The interface keeps the provider swappable. A cloud or AI extractor could be added later behind a config flag without touching `OcrService`.

### 2. Image preprocessing (`ReceiptImagePreprocessor`)

Thermal receipts are low-contrast. Preprocessing has more effect on accuracy than OCR engine settings.

| Step | Why |
|---|---|
| Grayscale | Removes color noise |
| Upscale so text height ≈ 30 px (cap long edge ~3000 px) | Tesseract is most accurate around 300 DPI-equivalent text |
| Light denoise (median 3×3) | Thermal paper speckle |
| Adaptive threshold (local mean / Sauvola) | Uneven lighting and faded print; a global threshold fails on phone photos |
| Deskew (±10°) | Photos are rarely straight; skew breaks line detection |

This can be done with plain Java2D for a first version. Move to OpenCV (`org.openpnp:opencv`) only if accuracy requires it.

This **replaces** the current width-only resize to 1024 px in `normalizeImage()`. That resize was sized for an AI model's token cost and shrinks small receipt text too much for Tesseract.

### 3. Tesseract configuration

| Setting | Value | Note |
|---|---|---|
| Languages | `vie+eng` | Handles both diacritic and plain uppercase text |
| Page segmentation mode | `4` (single column of variable-size text) | Fall back to `6` (single uniform block) if PSM 4 finds no total |
| Engine mode | `1` (LSTM only) | Best accuracy in Tesseract 5 |
| `preserve_interword_spaces` | `1` | Keeps the gap between item name and price |
| Instance reuse | One `Tesseract` instance per request, or a small pool | Tess4J instances are **not thread-safe** |

### 4. Parsing rules (`ReceiptTextParser`)

Pure function: `List<String> lines → ExtractedReceipt`. It's the easiest part to unit-test. Matching runs on an accent-stripped, uppercased copy of each line, so `TỔNG CỘNG` and `TONG CONG` are treated the same.

| Field | Rule |
|---|---|
| **Amounts** | Regex `\d{1,3}(?:[.,]\d{3})+(?![.,]\d)` or plain `\d{4,}`; strip separators. VND has no decimals, so `184.500` → `184500` |
| **Total** | Amount on (or the line right after) a line containing `TONG CONG`, `TONG TIEN`, `TONG THANH TOAN`, `THANH TIEN`, `TOTAL`. **Exclude** `KHACH TRA`/`KHACH DUA` (cash given), `TIEN THOI`/`TRA LAI` (change), `GIAM GIA` (discount), `VAT`/`THUE`. If several candidates match, take the last one before the payment section. If no keyword matches, use the largest amount in the bottom third of the receipt and add warning `TOTAL_GUESSED` |
| **Date** | `\b(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})\b`, read as **day/month/year**. Reject dates in the future or more than 1 year old (warning `DATE_SUSPICIOUS`). If missing, return `null`; the UI defaults the field to today |
| **Merchant** | Fuzzy-match (normalized Levenshtein ≥ 0.8) the first ~5 lines against merchant keywords already stored in `category_mappings` (ADR-005). If no match, use the first line with ≥ 3 letters that isn't an address, phone number or tax code (`MST`) |
| **Line items** | Lines between the header and the total line that end in an amount: text before the amount → `item`, last amount → `price`. Skip quantity × unit-price lines when they're followed by a total line. Best effort only |
| **Consistency** | If `sum(lineItems) ≠ total` (± discounts/VAT found), add warning `ITEMS_DO_NOT_MATCH_TOTAL`. Never "fix" the total from the items |

### 5. API contract changes

`ParsedReceiptResponse` changes so the UI can show what's uncertain:

- `merchant`, `date`, `totalAmount` become **nullable**. Today they are filled with fake values when unknown.
- Add `warnings: List<String>` (codes from the parser).
- Add `duplicateOfTransactionId: UUID?` so the "Possible duplicate → View" link in the design can navigate.
- Keep `lineItems` (may be empty).

Errors return problem-detail codes instead of a generic 500:

| Case | HTTP | Code | UI message |
|---|---|---|---|
| Unsupported or undecodable file | 415 / 400 | `UNSUPPORTED_FILE` | "Use a JPG, PNG or PDF" |
| OCR found almost no text (< ~20 characters) | 422 | `UNREADABLE` | "Could not read this receipt. Try a clearer photo." |
| Text found but no amount at all | 422 | `NOT_A_RECEIPT` | Same as above |
| OCR took longer than 15 s | 504 | `OCR_TIMEOUT` | "Taking too long — try a smaller photo" |

### 6. Saving line items

`TransactionRequest` gains an optional `lineItems` list. When it's present and `sourceType = OCR`, the line items are saved as `ReceiptItem` rows (the table already exists but nothing writes to it). The web client currently drops line items on save; that is fixed at the same time.

### 7. Upload and image handling

- **Client side** (`upload-page.tsx`): before uploading, draw the image on a canvas. That applies the photo's EXIF rotation, keeps the long edge at ≤ 2400 px, and exports JPEG at quality 0.9. This handles HEIC/WebP input the browser can decode (the server-side `ImageIO` cannot), cuts uploads from 3–8 MB to ~0.5 MB, and strips metadata.
- **Server side** (`application.yml`): raise Spring's default multipart limit (1 MB), which rejects most phone photos today:
  ```yaml
  spring:
    servlet:
      multipart:
        max-file-size: 10MB
        max-request-size: 10MB
  ```
- Image retention stays `NONE` (existing `app.ocr.image-retention`).

### 8. Configuration

```yaml
app:
  ocr:
    engine: tesseract            # future: other ReceiptExtractor implementations
    languages: vie+eng
    page-seg-mode: 4
    timeout: 15s
    max-concurrent: 2            # Tesseract is CPU-bound; bound it to protect the API
    tessdata-path: /usr/share/tesseract-ocr/5/tessdata
    pdf-dpi: 300
    image-retention: NONE
```

### 9. Docker image

Tess4J calls the native `libtesseract` through JNA. The current runtime image is Alpine (musl libc), where JNA and Leptonica bindings are a known source of trouble. Switch the runtime stage to the Ubuntu-based JRE image:

```dockerfile
FROM eclipse-temurin:21-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-vie tesseract-ocr-eng \
 && rm -rf /var/lib/apt/lists/*
```

The image grows by roughly 100 MB (Ubuntu base + Tesseract + language data). The build stage can stay on Alpine.

### 10. Observability (ADR-008)

| Signal | Name | Labels |
|---|---|---|
| Counter | `ocr_requests_total` | `outcome` = ok / unreadable / not_a_receipt / timeout / error |
| Counter | `ocr_field_found_total` | `field` = merchant / date / total / line_items |
| Counter | `ocr_warnings_total` | `code` |
| Histogram | `ocr_duration_seconds` | `stage` = preprocess / tesseract / parse |

- Add a span per stage under the existing request trace.
- Never log OCR text, merchant names or amounts: receipts are personal financial data.
- Replace the "token cost" panel planned for the Grafana OCR dashboard with "field found rate" and "p95 duration".

### 11. Testing

| Level | What |
|---|---|
| Unit | `ReceiptTextParser` with fixed text inputs: VN totals, `KHACH TRA`/`TIEN THOI` exclusion, `184.500` parsing, `dd/MM/yyyy`, missing fields |
| Unit | `OcrService` with `FakeReceiptExtractor`: duplicate flag, categorization, error mapping |
| Integration (Testcontainers / Docker) | `TesseractReceiptExtractor` on 3–4 synthetic receipt images rendered in the test, so the native library loads in CI |
| Accuracy set (manual, not in CI) | 15–20 real receipts (Bach Hoa Xanh, Circle K, WinMart, restaurant bills, a crumpled one, a faded one) with expected fields. Run after changing preprocessing or rules; track the % of receipts where total, date and merchant are correct |

**Target for v1:** total correct on ≥ 85% of clean receipts, date ≥ 85%, merchant ≥ 70%. Line items are best effort.

## Consequences

### Positive
- **Private:** receipt images and text never leave the server, consistent with `PRODUCT.md` principle 3.
- **Free and offline:** no API key, no per-receipt cost, works without internet.
- **No vendor dependency:** no pricing, model-deprecation or rate-limit risk.
- **Predictable and debuggable:** parsing rules are plain code with unit tests. A wrong result can be traced to a specific rule.
- **Swappable:** the `ReceiptExtractor` interface leaves room for a better engine later.

### Negative
- **Lower accuracy** than a vision model, especially on crumpled, faded, curved or low-light photos and on unusual layouts. Users will correct fields more often.
- **Line items are unreliable.** Expect frequent partial or messy results.
- **Rules need maintenance.** New receipt layouts or keywords mean new rules and test cases.
- **Heavier container:** about +100 MB, a native library dependency, and the move from Alpine to an Ubuntu-based JRE image.
- **CPU cost on the server:** about 1–3 s of CPU per receipt; concurrency must be bounded (`max-concurrent`).

### Neutral
- The review screen design (editable fields, duplicate banner, "items add up" check) works unchanged. Warnings map onto it.
- `PRODUCT.md` and `plan/README.md` need small wording updates: the OCR row in the tech stack, the privacy NFR and the architecture diagram.

## Alternatives Considered

**OpenAI Vision API (ADR-003)**
- Superseded: best accuracy and structured output, but sends receipts to a third party, adds recurring cost and an internet and vendor dependency. Can return later as an optional `ReceiptExtractor` behind a config flag if local accuracy proves insufficient.

**PaddleOCR as a sidecar container**
- Considered: noticeably better accuracy than Tesseract on photos, still local. Rejected for v1 because it requires a separate Python service, a larger image (~1 GB+) and an extra network hop. Revisit if Tesseract misses the accuracy target. The `ReceiptExtractor` interface allows this switch.

**Tesseract.js in the browser**
- Rejected: same engine but slower on low-end devices, ships ~10 MB+ of language data to the client, and would duplicate parsing logic in TypeScript and Java (future desktop and mobile clients need the same logic).

**E-invoice QR code decoding**
- Deferred: exact data when available, but many receipts' QR codes only contain a lookup link to the tax portal (with captcha). May be added later as a first pass: "QR found → exact data, otherwise OCR".

**No OCR (fast manual entry form)**
- Rejected as the primary path: most reliable, but contradicts the "no manual entry" product positioning. A manual quick-add form remains the fallback when OCR fails.

**On-device OCR on mobile (Google ML Kit / Apple Vision)**
- Not applicable to the web client. **Recommended for the future mobile app:** free, on-device and more accurate than Tesseract. The mobile app would send recognized text to a `POST /api/v1/ocr/parse-text` endpoint that reuses `ReceiptTextParser`.

## Implementation Plan

1. Add Tess4J dependency, switch the runtime Docker stage to `eclipse-temurin:21-jre`, and install `tesseract-ocr-vie`.
2. Introduce `ReceiptExtractor`, `ExtractedReceipt` and `FakeReceiptExtractor`, and refactor `OcrService` to use them (replace the stub).
3. Build `ReceiptTextParser` test-first against VN receipt text samples.
4. Build `ReceiptImagePreprocessor` and `TesseractReceiptExtractor`, plus the Docker-based integration test.
5. Change `ParsedReceiptResponse` (nullable fields, `warnings`, `duplicateOfTransactionId`), add error codes, and raise the multipart limits.
6. Add optional `lineItems` to `TransactionRequest` and persist `ReceiptItem` rows.
7. Regenerate shared types (`pnpm gen:types`). Update the receipt tab: client-side resize, editable fields, warnings, error states, ₫ formatting.
8. Add metrics and spans, and update the Grafana OCR panels.
9. Collect the 15–20 receipt accuracy set and tune preprocessing and rules until the v1 targets are met.

## References

- [ADR-003: Vision API for Receipt OCR](./003-vision-api-ocr.md) (superseded)
- [ADR-005: Keyword + Regex Categorization](./005-keyword-categorization.md): merchant keywords reused for merchant matching
- [ADR-008: Observability Stack](./008-observability-stack.md)
- [Web UI design: Receipt scan screen](../design/README.md) (page 05)
- Tesseract documentation: https://tesseract-ocr.github.io/tessdoc/
- Tess4J: https://github.com/nguyenq/tess4j
