# Finance Tracker

CSV + Receipt OCR Finance Tracker — a Spring Boot 3.x backend with PostgreSQL, multi-client API, and full Grafana observability stack.

## Architecture

Modular monolith with domain-driven packages:

```
com.financetracker/
├── auth/          # Authentication, JWT, API keys, security
├── transaction/   # Transaction CRUD, deduplication
├── csv/           # CSV import and parsing
├── ocr/           # Receipt OCR via AI Vision API
├── category/      # DB-backed merchant categorization
└── common/        # Cross-cutting: config, exceptions, filters, metrics
```

## Quick Start (Dev Profile)

**Prerequisites:** Java 21, Docker Desktop, Gradle 8.10

```bash
# 1. Start PostgreSQL
docker compose -f docker-compose.dev.yml up -d

# 2. Start the backend
cd backend
./gradlew bootRun
```

The app starts on **http://localhost:8080** with:
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health check: http://localhost:8080/actuator/health

## Quick Start (Full Stack)

```bash
# Start everything (backend + PostgreSQL + observability)
docker compose --profile all up -d
```

## API Overview

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/auth/register` | Register a new user |
| POST | `/api/v1/auth/login` | Login, get JWT token |
| POST | `/api/v1/csv/parse` | Upload & parse CSV |
| POST | `/api/v1/ocr/process` | Upload receipt image |
| POST | `/api/v1/transactions/batch` | Save confirmed transactions |
| GET | `/api/v1/transactions` | List transactions (paginated) |
| GET | `/api/v1/transactions/{id}` | Get single transaction |
| PUT | `/api/v1/transactions/{id}` | Update transaction |
| DELETE | `/api/v1/transactions/{id}` | Delete transaction |
| GET | `/api/v1/categories` | List available categories |
| GET | `/api/v1/categories/mappings` | List keyword→category mappings |

All authenticated endpoints require `Authorization: Bearer <token>` header.
Optional `X-Client-Id` header for multi-client identification.

## Observability Stack

| Service | Port | Purpose |
|---------|------|---------|
| Grafana | 3000 | Dashboards (pre-provisioned) |
| Prometheus | 9090 | Metrics storage |
| Tempo | 3200 | Distributed tracing |
| Loki | 3100 | Log aggregation |
| Alloy | 4317 | OTLP collector |

```bash
# Start only observability stack
docker compose --profile observability up -d
```

## Tech Stack

- **Backend:** Java 21 + Spring Boot 3.3 + Gradle
- **Database:** PostgreSQL 16 + Flyway migrations
- **Auth:** JWT (users) + API Key (devices)
- **CSV:** Apache Commons CSV with dynamic header mapping
- **OCR:** OpenAI Vision API (gpt-4o-mini)
- **Observability:** OpenTelemetry → Grafana Alloy → Prometheus + Tempo + Loki → Grafana
- **Docs:** SpringDoc OpenAPI / Swagger UI
