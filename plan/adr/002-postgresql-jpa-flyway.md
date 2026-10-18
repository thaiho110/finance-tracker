# ADR-002: PostgreSQL with Spring Data JPA + Flyway

## Status
Accepted

## Context
The Finance Tracker stores transaction records and receipt line items. Unlike a local-first single-user app, the system is designed for multi-user access and needs a production-grade relational database.

Requirements:
- ACID compliance for financial data integrity
- Multi-user concurrent access with row-level locking
- Type-safe Java repository pattern with Spring Boot
- Version-controlled, repeatable schema migrations
- Rich indexing for fast category/date queries
- No vendor lock-in; easy to self-host or cloud-host

## Decision
Use **PostgreSQL 16** as the database engine with **Spring Data JPA** (Hibernate) as the ORM and **Flyway** for schema migrations.

## Consequences

### Positive
- ACID-compliant — safe for financial transactions, even with concurrent users
- Row-level locking and MVCC — multiple users can read/write without conflicts
- Rich indexing (B-tree, GIN) for fast category, date, and merchant lookups
- Spring Data JPA provides type-safe repositories with zero boilerplate:
  ```java
  @Repository
  public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
      List<Transaction> findByDateBetweenAndAmount(LocalDate start, LocalDate end, BigDecimal amount);
      Page<Transaction> findByCategory(String category, Pageable pageable);
  }
  ```
- Flyway ensures reproducible schema across all environments (dev, test, prod)
- Flyway migrations are plain SQL — easy to review, version, and roll back
- Runs in Docker — `docker compose up` gives a working database instantly
- Scales vertically to millions of rows and horizontally via read replicas

### Negative
- Requires a running database server (Docker or local install)
- More operational overhead than SQLite (connection pool config, health checks)
- Cold start is slower (Docker pull + PostgreSQL init vs file open)
- Connection pool tuning needed for concurrent users

### Neutral
- Same JPA entities work with H2 (in-memory) for testing without changes
- PostgreSQL can be replaced with MySQL with minimal JPA config changes
- Flyway migrations are explicit — no auto-sync like Drizzle Kit

## Alternatives Considered

**SQLite (previous plan)**
- Rejected for revised stack: Single-user only, no concurrent write support, no native UUID type, limited for multi-user web app.

**H2 (in-memory)**
- Considered for development/testing only. Rejected for production — loses data on restart.

**Liquibase (over Flyway)**
- Considered: More feature-rich (rollback support, XML/YAML/JSON migrations). Flyway's SQL-first approach is simpler and sufficient for this project.

**MyBatis / JDBC Template (over JPA)**
- Rejected: More boilerplate, no automatic dirty checking, no lazy loading. JPA + Spring Data provides the best DX for this simple schema.

## References
- [Database Schema](../README.md#5-database-schema)
- [Flyway Migrations](../README.md#53-flyway-migration)
- [Spring Data JPA Documentation](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/)
