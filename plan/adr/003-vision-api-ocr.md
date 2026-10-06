# ADR-003: Vision API for Receipt OCR

## Status
Accepted

## Context
Receipt OCR requires extracting structured data (merchant name, date, total amount, category, line items) from uploaded receipt photos. Receipt quality varies (creases, glare, small fonts, different languages).

Requirements:
- Extract 5+ fields from a single receipt image
- Handle diverse receipt layouts (restaurant, retail, gas station)
- Return structured JSON, not raw text
- Low cost per receipt (target < $0.01/receipt)
- No infrastructure to manage

## Decision
Use **OpenAI Vision API (`gpt-4o-mini`)** with a structured JSON schema enforced via `response_format: { type: "json_object" }`.

## Consequences

### Positive
- Excellent accuracy on receipts — trained on diverse document layouts
- Returns structured JSON directly — no post-processing needed
- No infrastructure to manage (fully managed API)
- Low cost (~$0.15/M input tokens; ~500 tokens per receipt → ~$0.000075/receipt)
- Fast response (1–3 seconds for small images)
- Handles edge cases (rotated images, poor lighting) better than traditional OCR

### Negative
- Requires internet connection for each OCR request
- API key management and cost tracking needed
- Receipt images leave the local machine (privacy concern)
- Rate limits apply at higher volumes
- Vendor dependency — OpenAI could change pricing or deprecate the model

### Neutral
- Images can be resized client-side to reduce token cost before sending
- OpenAI does not train on API data by default (privacy mode available)

## Alternatives Considered

**Tesseract.js (local OCR)**
- Rejected: Poor accuracy on noisy/receipt images; outputs raw text, not structured JSON. Requires significant post-processing.

**Google Cloud Vision API**
- Considered: Good accuracy but more expensive ($1.50/1K images vs OpenAI's ~$0.075/1K receipts). Requires separate GCP setup.

**LlamaParse / other local models**
- Rejected: Requires GPU for acceptable speed; heavy local dependencies. Overkill for MVP.

**Azure Document Intelligence**
- Considered: Excellent for receipts but higher cost and more complex setup.

## References
- [OCR Flow](../README.md#42-receipt-ocr-flow)
- [OpenAI Vision API Documentation](https://platform.openai.com/docs/guides/vision)
