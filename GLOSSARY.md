# Finance Tracker

Personal finance tracking tool with CSV import, receipt OCR, and multi-client support.

## Language

**Transaction**:
A single financial record with a date, merchant, amount, and category. The core data entity of the system.
_Avoid_: Record, entry, line item

**ParsedTransaction**:
A transaction extracted from a CSV file, shown in a preview grid for user review before being saved. Not yet persisted.
_Avoid_: Raw transaction, CSV row

**Receipt**:
An uploaded receipt image that has been processed by OCR to extract merchant, date, total, and line items.
_Avoid_: Receipt image, photo, scan

**Category**:
A classification label assigned to a transaction (e.g., "Groceries", "Transportation"). Derived from keyword matching against the merchant name.
_Avoid_: Tag, label, group

**SourceType**:
The origin of a transaction. One of: CSV (imported from bank statement), OCR (extracted from receipt), MANUAL (entered by hand).
_Avoid_: Source, type, import method

**ApiClient**:
An HTTP client wrapper that handles authentication headers, token refresh, and `X-Client-Id` injection. Each platform provides its own implementation (web uses RTK Query with custom `baseQuery`; desktop and mobile use platform-specific HTTP clients).
_Avoid_: HTTP client, API wrapper, fetcher

**TokenStorage**:
An interface for persisting JWT tokens. Each platform provides its own: web uses `localStorage`, desktop uses Electron `safeStorage`, mobile uses `react-native-keychain`.
_Avoid_: Token store, keychain, storage

**Shared Types**:
Pure TypeScript interfaces auto-generated from the backend's OpenAPI spec using `openapi-typescript`. The single source of truth for API contracts shared across all clients.
_Avoid_: DTOs, models, generated types

**Aggregation Endpoint**:
A backend endpoint (`GET /api/v1/transactions/summary`) that returns pre-computed totals by category, monthly trends, and summary statistics. Avoids client-side computation over large datasets.
_Avoid_: Stats endpoint, dashboard API, summary API
