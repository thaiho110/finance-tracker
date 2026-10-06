# ADR-006: Spring Boot 3.x Backend with Java 21

## Status
Accepted

## Context
The Finance Tracker backend needs to handle file uploads (CSV and images), call an external Vision API, run business logic (categorization, deduplication), and persist data to PostgreSQL. The application is a REST API serving a Vite + React frontend.

Key forces:
- Team has Java/Spring Boot experience
- Need production-grade REST API with file upload support
- Need type-safe database access with JPA
- Need structured validation, error handling, and CORS configuration
- Java 21 LTS provides virtual threads and pattern matching

## Decision
Use **Spring Boot 3.x** with **Java 21** as the backend framework and language.

### Dependencies

| Dependency | Purpose |
|-----------|---------|
| `spring-boot-starter-web` | REST controllers, embedded Tomcat, JSON serialization |
| `spring-boot-starter-data-jpa` | JPA repositories, Hibernate ORM, entity management |
| `spring-boot-starter-validation` | Jakarta Bean Validation (`@Valid`, `@NotBlank`, `@Positive`) |
| `postgresql` | PostgreSQL JDBC driver |
| `flyway-core` + `flyway-postgresql` | Database migrations |
| `commons-csv` | CSV file parsing |
| `spring-boot-starter-test` | JUnit 5 + Mockito + Testcontainers |

## Consequences

### Positive
- **Rapid development** — auto-configuration, embedded server, starter dependencies
- **Production-ready** — health checks, metrics, graceful shutdown out of the box
- **Type-safe data access** — Spring Data JPA repositories with derived query methods
- **Structured validation** — `@Valid` + Jakarta annotations on DTOs
- **CORS support** — one-liner in `WebConfig` to allow Vite dev server
- **Virtual threads (Java 21)** — lightweight concurrency for Vision API calls
- **Large ecosystem** — every problem has a mature Spring solution
- **Testability** — JUnit 5 + `@WebMvcTest` + `@DataJpaTest` + Testcontainers

### Negative
- **JVM memory overhead** — ~200-400MB baseline vs Node.js ~50MB
- **Cold start** — 2-5 seconds vs Node.js ~200ms
- **More boilerplate** — DTOs, entities, repositories, services, controllers
- **Not full-stack JavaScript** — context switch between Java (backend) and TypeScript (frontend)

### Neutral
- Backend and frontend are separate processes — Docker Compose handles orchestration
- Java 21 virtual threads reduce thread-per-request overhead for Vision API calls
- Spring Boot 3.x requires Jakarta namespace (not javax) — all dependencies must be compatible

## Alternatives Considered

**Node.js / Express (previous plan)**
- Rejected for this iteration: User explicitly chose Spring Boot. Different ecosystem.

**Quarkus**
- Considered: Faster startup, lower memory, native compilation. Rejected because Spring Boot has a larger ecosystem, more tutorials, and the user prefers Spring.

**Kotlin + Spring Boot**
- Considered: Less boilerplate, null safety. Rejected because the user explicitly chose Java.

## Project Initialization

```bash
# Using Spring Initializr CLI
spring init \
  --build=maven \
  --java-version=21 \
  --group=com.financetracker \
  --artifact=finance-tracker \
  --name="Finance Tracker" \
  --description="CSV + Receipt OCR Finance Tracker" \
  --package-name=com.financetracker \
  --dependencies=web,data-jpa,postgresql,flyway,validation,lombok,devtools \
  backend/

# Or via Maven Archetype
mvn archetype:generate \
  -DgroupId=com.financetracker \
  -DartifactId=finance-tracker \
  -DarchetypeArtifactId=maven-archetype-quickstart
```

## References
- [Project Structure](../README.md#8-project-structure)
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Java 21 Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)
