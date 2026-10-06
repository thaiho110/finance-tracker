# ADR-005: Keyword + Regex Categorization

## Status
Accepted

## Context
Transactions need to be automatically categorized (e.g., "Coffee Shop", "Transportation", "Groceries"). The raw merchant name from CSV or OCR contains noise:
- Store numbers (e.g., "STARBUCKS #12345")
- Transaction IDs
- Location suffixes
- Different name formats for the same merchant

Requirements:
- Fast categorization — < 100ms per transaction
- No external API calls (offline-capable)
- Easy to extend with new categories and merchants
- Handle common merchant name variations

## Decision
Use a **keyword-based categorization engine** with regex merchant name cleaning and a static category mapping dictionary.

## Consequences

### Positive
- Extremely fast — simple string matching in memory
- Works completely offline — no API dependency
- Easy to understand and extend (just add entries to a dictionary)
- No training data or ML model required
- Deterministic results — predictable and debuggable

### Negative
- Cannot handle new or unknown merchants without manual mapping updates
- May mis-categorize generic merchants (e.g., "Target" could be "Shopping" or "Groceries")
- Requires ongoing maintenance as new merchants appear
- No fuzzy matching — "STARBUCKS" and "STARBUCKS COFFEE" need separate entries if different

### Neutral
- Can be extended with fuzzy matching (Levenshtein distance) in a future phase
- User overrides in preview grid provide a feedback loop for refinement

## Alternatives Considered

**ML-based classification (e.g., scikit-learn, Transformers)**
- Rejected: Over-engineered for MVP. Requires training data, model storage, and inference dependencies. 100x more complex for marginal improvement on common merchants.

**External API (e.g., Plaid, Yodlee)**
- Rejected: Requires bank account linking, not just CSV upload. Privacy concerns. Costly per-transaction.

**LLM-based categorization**
- Considered: Could batch-categorize with Vision API calls. Adds latency and cost. Good as a future enhancement for unknown merchants.

## Implementation Sketch

```typescript
const categoryMap: Record<string, string> = {
  // Coffee & Cafes
  STARBUCKS: "Coffee Shop",
  "COSTA COFFEE": "Coffee Shop",
  DUNKIN: "Coffee Shop",

  // Food & Dining
  MCDONALDS: "Fast Food",
  "KFC": "Fast Food",
  "DOMINO'S": "Fast Food",
  UBER: "Transportation",
  GRAB: "Transportation",

  // Shopping
  AMAZON: "Online Shopping",
  WALMART: "Shopping",
  TARGET: "Shopping",

  // Utilities
  ELECTRIC: "Utilities",
  WATER: "Utilities",
  INTERNET: "Utilities",
  PHONE: "Utilities",
};

function cleanMerchant(raw: string): string {
  return raw
    .toUpperCase()
    .replace(/#\d+/g, "")      // Remove store numbers
    .replace(/\b\d{10,}\b/g, "") // Remove transaction IDs
    .replace(/\s+/g, " ")       // Normalize whitespace
    .trim();
}

function categorize(cleaned: string): string {
  for (const [keyword, category] of Object.entries(categoryMap)) {
    if (cleaned.includes(keyword)) return category;
  }
  return "Other";
}
```

## References
- [Processing Flow](../README.md#43-processing--validation-flow)
