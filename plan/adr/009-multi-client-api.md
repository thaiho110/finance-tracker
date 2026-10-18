# ADR-009: Multi-Client API Design (Versioning, Auth, CORS)

## Status
Accepted

## Context
The Finance Tracker backend currently serves a single Vite + React frontend. Future clients include:
- **Mobile** — Android/iOS (native apps via Retrofit/Ktor client)
- **Desktop** — Electron/Tauri app
- **Dedicated Devices** — Receipt scanners, IoT devices with API key auth

These clients have different:
- Authentication mechanisms (JWT for users, API keys for devices)
- Rate limit requirements
- UI capabilities (mobile has smaller screens, devices may not have UI at all)
- Deployment origins (different CORS origins for web vs mobile vs desktop)

Requirements:
- Must not break existing web client when adding new clients
- Must provide clear documentation for third-party client developers
- Must identify which client is making each request
- Must support graceful API evolution over time

## Decision
Implement a **versioned REST API** (`/api/v1/`) with **client identification** via `X-Client-Id` header, **JWT + API key dual auth**, and **OpenAPI documentation** via SpringDoc.

## Consequences

### Positive
- **Backward compatibility** — URL versioning (`/api/v1/`) ensures existing clients continue working when new versions are released
- **Client visibility** — `X-Client-Id` header enables per-client metrics, rate limiting, and feature flags:
  ```java
  @Component
  public class ClientIdFilter extends OncePerRequestFilter {
      @Override
      protected void doFilterInternal(HttpServletRequest request,
              HttpServletResponse response, FilterChain chain) {
          String clientId = request.getHeader("X-Client-Id");
          ClientContext.set(clientId != null ? clientId : "unknown");
          chain.doFilter(request, response);
      }
  }
  ```
- **Dual auth** — JWT for user-facing clients, API key for devices (stateless, simple)
- **Rate limiting** — Configurable per `X-Client-Id` via Spring Cloud Gateway or interceptor
- **OpenAPI docs** — Auto-generated via SpringDoc, accessible at `/swagger-ui.html`:
  ```xml
  <dependency>
      <groupId>org.springdoc</groupId>
      <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
      <version>2.6.0</version>
  </dependency>
  ```
- **Consistent error contract** — Every client gets the same error JSON structure regardless of auth mechanism
- **Sunset header** — Deprecated API versions announce their removal date:
  ```java
  @GetMapping("/api/v1/transactions")
  public ResponseEntity<List<Transaction>> listV1() {
      return ResponseEntity.ok()
          .header("Sunset", "Sat, 01 Nov 2027 23:59:59 GMT")
          .body(service.findAll());
  }
  ```

### Negative
- **URL versioning is permanent** — Once `/api/v1/` is public, it must be maintained or clearly deprecated
- **More boilerplate** — Version prefixes in all controller mappings, auth filter, client ID filter
- **Rate limiting adds complexity** — Must track per-client request counts (in-memory or Redis)
- **Dual auth doubles testing surface** — Must test every endpoint with JWT and API key

### Neutral
- BFF (Backend-for-Frontend) pattern is deferred — not needed until client-specific logic emerges
- API version can be moved to a header (`Accept-Version`) later if URL-based versioning proves limiting
- Client ID can be used for A/B testing new features per client type

## Alternatives Considered

**No versioning (single `/api/` endpoint)**
- Rejected: Breaks all existing clients on any breaking change. No migration path.

**Header-based versioning (`Accept-Version: v1`)**
- Considered: Cleaner URLs but harder to discover and test. URL versioning is more explicit and cache-friendly.

**BFF per client type**
- Deferred: Adding a BFF layer (Node.js for web, Ktor for mobile) adds deployment complexity. Direct API calls are sufficient for MVP.

**No client identification**
- Rejected: Cannot distinguish clients for rate limiting, metrics, or feature flags.

**OAuth2 for device auth**
- Rejected: Overkill for dedicated devices. API key + TLS is simpler and sufficient for controlled devices.

## Auth Flow

### JWT Auth (Web / Mobile / Desktop)

```
POST /api/v1/auth/login  { email, password }
        │
        ▼
  200 OK  { token: "eyJhbG...", refreshToken: "..." }
        │
        ▼
  Subsequent requests:
  GET /api/v1/transactions
  Authorization: Bearer eyJhbG...
  X-Client-Id: finance-tracker-web
```

### API Key Auth (Dedicated Devices)

```
GET /api/v1/transactions
X-Api-Key: ft-device-key-abc123
X-Client-Id: finance-tracker-device-01
```

## API Version Lifecycle

| Phase | Convention | Example |
|-------|-----------|---------|
| **Active** | Full support | `/api/v1/transactions` |
| **Deprecated** | Still works, `Sunset` header added | `/api/v1/transactions` + `Sunset: ...` |
| **Sunset** | Returns `410 Gone` | — |
| **Removed** | Returns `404` | — |

## References
- [Multi-Client API Design](../README.md#5-multi-client-api-design)
- [API Contract](../README.md#54-api-contract-v1)
- [SpringDoc OpenAPI](https://springdoc.org/)
- [Microsoft REST API Guidelines](https://github.com/microsoft/api-guidelines)
