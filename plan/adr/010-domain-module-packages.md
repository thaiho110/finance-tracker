# ADR-010: Domain Module Package Structure

## Status
Accepted

## Context
The initial codebase used a flat package structure (`controller/`, `service/`, `model/`, `repository/`, `config/`, `filter/`) where all modules coexisted at the same level. While the architecture plan (ADR-001) specified a "Modular Monolith with clear module boundaries", the flat structure provided no compile-time enforcement of those boundaries.

As the project grew, this caused:
- **Implicit coupling** — any service could import any other service without a clear dependency direction
- **Discovery friction** — finding all files related to a domain (e.g., OCR) required scanning 6+ directories
- **Onboarding confusion** — new developers couldn't see the domain boundaries from the package layout

The grilling session (Round 1, Q1) identified this gap between the architectural intent and the code structure.

## Decision
Restructure the codebase into **domain-module packages**, where each domain owns its complete vertical slice:

```
com.financetracker/
├── auth/          — Authentication, JWT, API keys, security config, User entity
├── transaction/   — Transaction CRUD, receipt items, deduplication
├── csv/           — CSV import and parsing
├── ocr/           — Receipt OCR via AI Vision API
├── category/      — DB-backed merchant categorization
└── common/        — Cross-cutting concerns (config, exceptions, filters, shared DTOs)
```

Each module package contains its own:
- `api/` — REST controllers
- `dto/` — Request/response DTOs
- `model/` — JPA entities
- `repository/` — Spring Data repositories
- `service/` — Business logic

The `common/` module holds cross-cutting concerns that don't belong to a single domain.

## Consequences

### Positive
- **Domain isolation** — Each module is a self-contained vertical slice; dependencies between modules are explicit
- **Discovery** — All OCR-related code is under `ocr/`, all transaction code under `transaction/`
- **Future extraction** — If a module ever needs to become a microservice, its code is already colocated
- **Onboarding** — New developers see the domain boundaries immediately from the package tree
- **Compile-time clarity** — Imports between modules are visible and reviewable

### Negative
- **Slightly more package directories** — 6 modules × up to 5 sub-packages each
- **Cross-module references still allowed** — Since this is a monolith (one jar), there's no strict module boundary enforcement at compile time (no Java modules or OSGi)
- **Refactoring cost** — Moving files into new packages was a one-time effort; going forward, new files must be placed in the correct module

### Neutral
- `common/dto/ParsedTransactionResponse` lives in `common/` because both `csv/` and `transaction/` reference it
- `category/` depends on `transaction/` only via `TransactionRepository` in the deduplication flow

## Alternatives Considered

**Clean Architecture layers (domain/application/infrastructure/presentation)**
- Rejected: Over-engineered for this project's scale. Domain modules map more naturally to the business domains (CSV, OCR, Auth, Transaction).

**Flat structure (original)**
- Rejected: No boundary enforcement, poor discoverability.

**Java Platform Module System (JPMS)**
- Rejected: Too much overhead for a modular monolith; requires module-info.java in every module.

## References
- [ADR-001: Modular Monolith Architecture](../plan/adr/001-modular-monolith.md)
- [Grilling Session Q1](../README.md#round-1)
