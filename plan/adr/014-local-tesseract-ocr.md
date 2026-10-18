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

Replace the planned Vision API call with **Tesseract OCR running locally in the backend container** (the `tesseract` command-line program, started as a subprocess), with **image preprocessing** before OCR and **rule-based parsing** after it. No receipt data leaves the server.

> Tesseract 4+ uses an LSTM neural network internally, but it is an open-source library executed locally, with no API calls, keys or per-request cost.

### Pipeline

```
upload (jpg/png/pdf)
  → [client] downscale + re-encode to JPEG, apply EXIF rotation
  → [OcrService] validate, PDF → image (existing), decode
  → [ReceiptImagePreprocessor] grayscale → rescale → denoise → deskew
  → [TesseractReceiptOcrEngine] tesseract CLI (vie+eng, PSM 6; retry PSM 4 if no total) → text
  → [ReceiptTextParser] rules → merchant, date, total, line items, warnings
  → [OcrService] categorize + dedup (existing) → ParsedReceiptResponse
  → image bytes discarded (existing behaviour)
```

### 1. OCR behind an interface, parsing as a pure function

```java
public interface ReceiptOcrEngine {
    enum Layout { PRIMARY, FALLBACK }
    String recognize(BufferedImage image, Layout layout);   // raw OCR text
}

// OcrService: text = engine.recognize(image, PRIMARY); receipt = parser.parse(lines, knownMerchants, today)
public final class ReceiptTextParser {
    public ExtractedReceipt parse(List<String> lines, Collection<String> knownMerchants, LocalDate today);
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

- `TesseractReceiptOcrEngine` is the production implementation.
- Unit tests mock `ReceiptOcrEngine` to return fixed text, so `OcrServiceTest` exercises the real parser.
- The interface keeps the engine swappable. Another engine (PaddleOCR, a cloud API) could be added later without touching `OcrService` or the parser.

### 2. Image preprocessing (`ReceiptImagePreprocessor`)

Thermal receipts are low-contrast. Preprocessing has more effect on accuracy than OCR engine settings.

| Step | Why |
|---|---|
| Grayscale | Removes color noise |
| Upscale so text height ≈ 30 px (cap long edge ~3000 px) | Tesseract is most accurate around 300 DPI-equivalent text |
| Light denoise (median 3×3) | Thermal paper speckle |
| Deskew (±10°) | Photos are rarely straight; skew breaks line detection. The angle is found with a projection profile on a locally thresholded (Bradley) sample, so a dark table behind the receipt doesn't hide the text lines |

**No final binarization.** The output stays grayscale. In testing, adding an adaptive threshold before Tesseract made bold digits merge ("184.500" read as "184.5080") and lost amounts. Tesseract binarizes internally and did better on the grayscale image.

This can be done with plain Java2D for a first version. Move to OpenCV (`org.openpnp:opencv`) only if accuracy requires it.

This **replaces** the current width-only resize to 1024 px in `normalizeImage()`. That resize was sized for an AI model's token cost and shrinks small receipt text too much for Tesseract.

### 3. Tesseract configuration

| Setting | Value | Note |
|---|---|---|
| Languages | `vie+eng` | Handles both diacritic and plain uppercase text |
| Page segmentation mode | `6` (single uniform block of text) | If no total is found, retry with `4` (single column) and keep whichever result has more fields. PSM 6 kept right-aligned amounts on the same line as their labels; PSM 4 often split them |
| Engine mode | `1` (LSTM only) | Best accuracy in Tesseract 5 |
| `preserve_interword_spaces` | `1` | Keeps the gap between item name and price |
| Process model | One `tesseract` process per recognition; image piped via stdin, text read from stdout | No temp files, no shared state. At most `max-concurrent` run at once (semaphore); waiting longer than the timeout fails with `OCR_BUSY` |

### 4. Parsing rules (`ReceiptTextParser`)

Pure function: `List<String> lines → ExtractedReceipt`. It's the easiest part to unit-test. Matching runs on an accent-stripped, uppercased copy of each line, so `TỔNG CỘNG` and `TONG CONG` are treated the same.

| Field | Rule |
|---|---|
| **Amounts** | Regex `\d{1,3}(?:[.,]\d{3})+(?![.,]\d)` or plain `\d{4,}`; strip separators. VND has no decimals, so `184.500` → `184500` |
| **Total** | Amount on (or the line right after) a line containing a total keyword, by priority: `TONG THANH TOAN` / `KHACH PHAI TRA` / `CAN THANH TOAN` > `TONG CONG` / `TONG TIEN` > `TONG` / `TOTAL` > `THANH TOAN` > `TONG TIEN HANG` / `THANH TIEN` (subtotal, column header). Keywords of 9+ letters also match with OCR mistakes (similarity ≥ 0.8). **Exclude** `KHACH TRA`/`KHACH DUA` (cash given), `TIEN THOI`/`TRA LAI` (change), `GIAM GIA` (discount), `VAT`/`THUE`. If several candidates match, take the last one before the payment section. If no keyword matches, use the largest amount in the bottom third of the receipt and add warning `TOTAL_GUESSED` |
| **Date** | `\b(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})\b`, read as **day/month/year**. Reject dates in the future or more than 1 year old (warning `DATE_SUSPICIOUS`). If missing, return `null`; the UI defaults the field to today |
| **Merchant** | Fuzzy-match (normalized Levenshtein ≥ 0.8) the first ~5 lines against merchant keywords already stored in `category_mappings` (ADR-005). If no match, use the first line with ≥ 3 letters that isn't an address, phone number or tax code (`MST`) |
| **Line items** | Lines before the total line that end in an amount: text before the trailing number/quantity/currency tokens → `item`, last amount → `price`. A line with only numbers (`2 x 12.000  24.000`) takes its name from the line above. A line whose amount equals the sum of the items above it is a subtotal, not an item. Best effort only |
| **Consistency** | If `sum(lineItems) ≠ total` (± discounts/VAT found), add warning `ITEMS_DO_NOT_MATCH_TOTAL`. Never "fix" the total from the items |

### 5. API contract changes

`ParsedReceiptResponse` changes so the UI can show what's uncertain:

- `merchant`, `date`, `totalAmount` become **nullable**. Today they are filled with fake values when unknown.
- Add `warnings: List<String>` (codes from the parser).
- *(Deferred)* `duplicateOfTransactionId: UUID?` so the "Possible duplicate → View" link can open the matching transaction. For now "View" opens the transaction list.
- Keep `lineItems` (may be empty).

Errors return problem-detail codes instead of a generic 500:

| Case | HTTP | Code | UI message |
|---|---|---|---|
| Unsupported or undecodable file | 415 / 400 | `UNSUPPORTED_FILE` | "Use a JPG, PNG or PDF" |
| OCR found almost no text (< ~20 characters) | 422 | `UNREADABLE` | "Could not read this receipt. Try a clearer photo." |
| Text found but no amount at all | 422 | `NOT_A_RECEIPT` | Same as above |
| OCR took longer than 15 s | 504 | `OCR_TIMEOUT` | "Taking too long — try a smaller photo" |
| All OCR slots busy for longer than the timeout | 503 | `OCR_BUSY` | "Try again in a moment" |
| Tesseract missing or crashed | 500 | `OCR_FAILED` | "Receipt reading isn't available" |
| Upload over 10 MB | 413 | `FILE_TOO_LARGE` | "This file is larger than 10MB" |

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

Bound to `OcrProperties` (`@ConfigurationProperties("app.ocr")`):

```yaml
app:
  ocr:
    languages: vie+eng
    page-seg-mode: 6
    fallback-page-seg-mode: 4    # 0 disables the retry
    timeout: 15s
    max-concurrent: 2            # Tesseract is CPU-bound; bound it to protect the API
    pdf-dpi: 300
    image-retention: NONE
    tesseract:
      command: ${TESSERACT_CMD:tesseract}
      tessdata-path: ${TESSDATA_PATH:}   # blank = Tesseract's default
```

### 9. Docker image

Install Tesseract and the language data in the runtime image. The runtime stage moves from Alpine to the Ubuntu-based JRE, whose packages provide Tesseract 5.3, the version this was tested with:

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
| Counter | `finance.ocr.receipts` | `outcome` = ok / unsupported_file / unreadable / not_a_receipt / ocr_timeout / ocr_busy / ocr_failed / error |
| Counter | `finance.ocr.fields.found` | `field` = merchant / date / total / line_items, `found` = true / false |
| Counter | `finance.ocr.warnings` | `code` |
| Timer | `finance.ocr.duration` | `outcome` |

- Add a span per stage under the existing request trace.
- Never log OCR text, merchant names or amounts: receipts are personal financial data.
- Replace the "token cost" panel planned for the Grafana OCR dashboard with "field found rate" and "p95 duration".

### 11. Testing

| Level | What |
|---|---|
| Unit | `ReceiptTextParser` with fixed text inputs: VN totals, `KHACH TRA`/`TIEN THOI` exclusion, `184.500` parsing, `dd/MM/yyyy`, missing fields |
| Unit | `OcrService` with a mocked `ReceiptOcrEngine` returning fixed text: duplicate flag, categorization, error mapping |
| Integration | `TesseractCliTest` runs the real `tesseract` binary on an image rendered in the test; skipped automatically when Tesseract isn't installed |
| Accuracy set (manual, not in CI) | 15–20 real receipts (Bach Hoa Xanh, Circle K, WinMart, restaurant bills, a crumpled one, a faded one) with expected fields. Run after changing preprocessing or rules; track the % of receipts where total, date and merchant are correct |

**Target for v1:** total correct on ≥ 85% of clean receipts, date ≥ 85%, merchant ≥ 70%. Line items are best effort.

## Implementation Notes (2026-10-18)

Built on branch `feature/local-tesseract-ocr`. Differences from the first draft of this ADR, based on testing:

- **Tesseract CLI instead of Tess4J.** No new Maven dependency, no JNA/native bindings (works on any base image with `tesseract` installed), no thread-safety concerns, and a hard per-call timeout by killing the process. The cost is ~10–20 ms to start a process, which is negligible next to ~1 s of OCR.
- **Grayscale input, PSM 6 first.** Measured on four synthetic receipt photos (clean; skewed 3.5° with diacritics and noise; dim, skewed, on a dark background; tiny 273 px photo):

  | Setup | Merchant / date / total correct |
  |---|---|
  | Raw photo, PSM 4 | 7 / 12 |
  | Black-and-white preprocessing, PSM 4 | 7 / 12 |
  | Raw photo, PSM 6 | 11 / 12 (tiny photo: date lost, total only guessed) |
  | Grayscale preprocessing, PSM 6 *(chosen)* | 11 / 12 (tiny photo: total misread as 78,000, flagged by `ITEMS_DO_NOT_MATCH_TOTAL`) |

  The page segmentation mode mattered most. On this small synthetic set, grayscale preprocessing is not clearly better than none once PSM 6 is used. It is kept because deskewing and upscaling are what real, tilted phone photos need, and it didn't hurt here, but the real-receipt accuracy set should confirm this; `ReceiptImagePreprocessor` is easy to bypass if not. About 0.7–1.6 s per receipt on 2 vCPUs.
- Synthetic images are not real phone photos. The accuracy set of 15–20 real receipts (§11) is still needed to confirm the v1 targets.

## Consequences

### Positive
- **Private:** receipt images and text never leave the server, consistent with `PRODUCT.md` principle 3.
- **Free and offline:** no API key, no per-receipt cost, works without internet.
- **No vendor dependency:** no pricing, model-deprecation or rate-limit risk.
- **Predictable and debuggable:** parsing rules are plain code with unit tests. A wrong result can be traced to a specific rule.
- **Swappable:** the `ReceiptOcrEngine` interface leaves room for a better engine later.

### Negative
- **Lower accuracy** than a vision model, especially on crumpled, faded, curved or low-light photos and on unusual layouts. Users will correct fields more often.
- **Line items are unreliable.** Expect frequent partial or messy results.
- **Rules need maintenance.** New receipt layouts or keywords mean new rules and test cases.
- **Heavier container:** about +100 MB (Tesseract + language data) and the move from Alpine to an Ubuntu-based JRE image.
- **CPU cost on the server:** about 1–3 s of CPU per receipt; concurrency must be bounded (`max-concurrent`).

### Neutral
- The review screen design (editable fields, duplicate banner, "items add up" check) works unchanged. Warnings map onto it.
- `PRODUCT.md` and `plan/README.md` need small wording updates: the OCR row in the tech stack, the privacy NFR and the architecture diagram.

## Alternatives Considered

**OpenAI Vision API (ADR-003)**
- Superseded: best accuracy and structured output, but sends receipts to a third party, adds recurring cost and an internet and vendor dependency. Can return later as another `ReceiptOcrEngine` behind a config flag if local accuracy proves insufficient.

**PaddleOCR as a sidecar container**
- Considered: noticeably better accuracy than Tesseract on photos, still local. Rejected for v1 because it requires a separate Python service, a larger image (~1 GB+) and an extra network hop. Revisit if Tesseract misses the accuracy target. The `ReceiptOcrEngine` interface allows this switch.

**Tesseract.js in the browser**
- Rejected: same engine but slower on low-end devices, ships ~10 MB+ of language data to the client, and would duplicate parsing logic in TypeScript and Java (future desktop and mobile clients need the same logic).

**E-invoice QR code decoding**
- Deferred: exact data when available, but many receipts' QR codes only contain a lookup link to the tax portal (with captcha). May be added later as a first pass: "QR found → exact data, otherwise OCR".

**No OCR (fast manual entry form)**
- Rejected as the primary path: most reliable, but contradicts the "no manual entry" product positioning. A manual quick-add form remains the fallback when OCR fails.

**On-device OCR on mobile (Google ML Kit / Apple Vision)**
- Not applicable to the web client. **Recommended for the future mobile app:** free, on-device and more accurate than Tesseract. The mobile app would send recognized text to a `POST /api/v1/ocr/parse-text` endpoint that reuses `ReceiptTextParser`.

## Implementation Plan

1. ✅ Switch the runtime Docker stage to `eclipse-temurin:21-jre` and install `tesseract-ocr-vie`.
2. ✅ Introduce `ReceiptOcrEngine` and `ExtractedReceipt`, and refactor `OcrService` to use them (replace the stub).
3. ✅ Build `ReceiptTextParser` test-first against VN receipt text samples.
4. ✅ Build `ReceiptImagePreprocessor`, `TesseractCli` and `TesseractReceiptOcrEngine`, plus a test against the real binary.
5. ✅ Change `ParsedReceiptResponse` (nullable fields, `warnings`), add error codes, and raise the multipart limits. *(`duplicateOfTransactionId` deferred.)*
6. ✅ Add optional `lineItems` to `TransactionRequest` and persist `ReceiptItem` rows.
7. ✅ Update the receipt tab: client-side resize and rotation, warnings, error codes, line items on save. *(Regenerate shared types with `pnpm gen:types` once the backend runs.)*
8. 🟡 Metrics added; spans and Grafana OCR panels still to do.
9. ⬜ Collect the 15–20 receipt accuracy set and tune preprocessing and rules until the v1 targets are met.

## References

- [ADR-003: Vision API for Receipt OCR](./003-vision-api-ocr.md) (superseded)
- [ADR-005: Keyword + Regex Categorization](./005-keyword-categorization.md): merchant keywords reused for merchant matching
- [ADR-008: Observability Stack](./008-observability-stack.md)
- [Web UI design: Receipt scan screen](../design/README.md) (page 05)
- Tesseract documentation: https://tesseract-ocr.github.io/tessdoc/
- Tesseract command-line usage: https://tesseract-ocr.github.io/tessdoc/Command-Line-Usage.html
