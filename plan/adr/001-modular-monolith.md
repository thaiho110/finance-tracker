# ADR-001: Modular Monolith Architecture

## Status
Accepted

## Context
The Finance Tracker is a single-user / small-team application with a well-defined but limited scope: CSV import, receipt OCR, categorization, deduplication, and data storage. The team is 1–3 developers. There are no independent scaling requirements for different modules, and the entire application can run locally without a server.

Key forces:
- Small team with limited DevOps bandwidth
- Desire for rapid iteration and simple deployment
- Clear module boundaries exist (CSV, OCR, Processing, Storage)
- No need to scale individual components independently

## Decision
Use a **Modular Monolith** architecture — a single deployable application with well-defined module boundaries and internal interfaces between modules.

## Consequences

### Positive
- Single deployment unit — easy to build, test, and deploy
- No network overhead between modules
- Shared database transaction scope across modules
- Simpler debugging and observability
- Easy to extract modules into microservices later if needed

### Negative
- Cannot scale individual modules independently
- Single process — a crash takes down everything
- Technology lock-in (all modules share the same runtime)

### Neutral
- Module boundaries require team discipline to maintain
- Future extraction to microservices is possible but requires interface refactoring

## Alternatives Considered

**Flat Monolith (no module boundaries)**
- Rejected: Without module boundaries, the codebase becomes tightly coupled and hard to maintain as features grow.

**Microservices**
- Rejected: Premature for a 1–3 person team. Adds network complexity, container orchestration, and distributed debugging overhead without proportional benefit.

**Serverless (AWS Lambda)**
- Rejected: Vendor lock-in, cold starts for infrequent use, and higher cost for a local-first tool. Better suited for variable, high-scale workloads.

## References
- [Architecture Patterns: Monolith vs Microservices](../README.md#2-architecture-pattern-modular-monolith)
