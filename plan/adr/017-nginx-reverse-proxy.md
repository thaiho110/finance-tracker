# ADR-017: nginx Reverse Proxy and Local URLs

**Status:** Proposed  
**Date:** 2026-10-18  
**Deciders:** Thai Ho  
**Related:** [ADR-009](./009-multi-client-api.md), [ADR-011](./011-frontend-monorepo.md), [ADR-016](./016-admin-area.md)

## Context

Running the stack today means remembering ports: backend on `:8080`, Grafana on `:3000`, Prometheus `:9090`, Tempo `:3200`, Loki `:3100`, Alloy `:4317/4318/12345`, Postgres `:5432`, and the web app only through `pnpm dev` on `:5173`. There is no container for the web app, so `docker compose up` does not give a usable app.

Problems this causes:

- No single URL to open the app; every service is published on the host.
- The web app is only reachable through the Vite dev server; nothing serves a production build.
- CORS is configured **twice** (`WebConfig` and `SecurityConfig`) with hardcoded `localhost` origins.
- Grafana is published with anonymous **Admin** access.
- Postgres is published on the host with default credentials.

The web app already calls the API with a relative base URL (`/api/v1`), so serving the SPA and the API from one origin needs no frontend change.

## Decision

Add an **nginx** container as the only entry point. It serves the built web app and proxies the API, under readable hostnames. Everything else stays on the internal Docker network.

### Hostnames

| URL | Goes to | Notes |
|---|---|---|
| `http://finance.localhost` | nginx → web build (static) | SPA, history fallback to `index.html` |
| `http://finance.localhost/api/` | nginx → `backend:8080` | Same origin, so no CORS for the web app |
| `http://grafana.finance.localhost` | nginx → `grafana:3000` | Grafana login (anonymous access off) |

`*.localhost` resolves to `127.0.0.1` in Chrome, Edge and Firefox with no setup. Tools that do not (some CLI clients on Windows) need one hosts-file line: `127.0.0.1 finance.localhost grafana.finance.localhost`.

The hostnames come from `.env` (`APP_HOST`, `GRAFANA_HOST`) so the same config works later on the home server with a LAN name (e.g. `finance.home` via Pi-hole local DNS) or a real domain.

### Containers

```
                         host :80 (and :443 later)
                                   │
                              ┌────▼────┐
                              │  nginx  │  serves /usr/share/nginx/html (web build)
                              └─┬─────┬─┘
                    /api/       │     │  grafana host
                         ┌──────▼─┐ ┌─▼───────┐
                         │backend │ │ grafana │
                         └───┬────┘ └────┬────┘
                         postgres   prometheus · loki · tempo
                                         ▲
                                       alloy  ◄── OTLP from backend
```

- **`web` build stage**: `packages/web/Dockerfile` (or a stage in `config/nginx/Dockerfile`): `node:22-alpine` + `corepack enable` + `pnpm install --frozen-lockfile` + `pnpm --filter @finance-tracker/web build`, then copy `packages/web/dist` into an `nginx:1.27-alpine` image. One image, `finance-tracker-nginx`, contains config and static files.
- **Published ports:** only nginx `80` (and `443` later). Backend, Postgres, Grafana and the observability services lose their `ports:` in the default file.
- **`docker-compose.ports.yml`** (new override) re-publishes host ports for debugging (Postgres for a DB client, `8080`, Prometheus and Alloy UIs) and is used with `-f docker-compose.yml -f docker-compose.ports.yml`. It is deliberately not named `docker-compose.override.yml`, which Compose would load automatically. The existing `docker-compose.dev.yml` (standalone Postgres + pgAdmin for running the backend from the IDE) is unchanged.
- nginx belongs to profiles `app` and `all`.

### nginx configuration (highlights)

`config/nginx/templates/default.conf.template` (the official image runs `envsubst` on templates at start):

```nginx
server {
    listen 80;
    server_name ${APP_HOST};

    client_max_body_size 15m;            # receipt photos and PDFs
    gzip on;
    gzip_types text/css application/javascript application/json image/svg+xml;

    add_header X-Content-Type-Options nosniff always;
    add_header Referrer-Policy strict-origin-when-cross-origin always;
    add_header X-Frame-Options DENY always;

    root /usr/share/nginx/html;

    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;            # OCR can take several seconds
    }

    location = /actuator/health { proxy_pass http://backend:8080; }
    location /actuator/ { return 404; }  # metrics only on the internal network

    location /assets/ { expires 1y; add_header Cache-Control "public, immutable"; }
    location = /index.html { add_header Cache-Control "no-cache"; }
    location / { try_files $uri $uri/ /index.html; }
}

server {
    listen 80;
    server_name ${GRAFANA_HOST};
    location / {
        proxy_pass http://grafana:3000;
        proxy_set_header Host $host;
        proxy_set_header Upgrade $http_upgrade;     # Grafana live
        proxy_set_header Connection "upgrade";
    }
}

server {                                          # anything else
    listen 80 default_server;
    return 444;
}
```

### Backend changes

- `server.forward-headers-strategy: framework` so request URLs, redirects and logs see the client's scheme and IP, and the rate limiter (ADR-009) can key on the real client IP for anonymous endpoints.
- One CORS configuration (delete the copy in `WebConfig`), with origins from `app.cors.allowed-origins` (env `CORS_ALLOWED_ORIGINS`). Defaults: `http://localhost:5173` (Vite dev) and `http://${APP_HOST}`. Native clients (Capacitor, Electron) are added when those apps exist.
- Swagger only in the `dev` profile (ADR-016); when enabled it is reachable at `/api/docs` through nginx (`springdoc.swagger-ui.path`).

### Grafana

- `GF_SERVER_ROOT_URL=http://${GRAFANA_HOST}`, `GF_AUTH_ANONYMOUS_ENABLED=false`, `GF_SECURITY_ADMIN_PASSWORD=${GRAFANA_ADMIN_PASSWORD}` from `.env`.
- Linked from the admin Status page (ADR-016).

### TLS (later, not in this ADR's first step)

- **Local:** `mkcert` certificates for `*.finance.localhost`, mounted into nginx, `listen 443 ssl` + redirect from 80.
- **Home server / public:** Let's Encrypt via a certbot sidecar, or a Cloudflare Tunnel so no port is opened on the router. Chosen when the deployment target is decided (a separate ADR).

### Observability

nginx access logs in JSON to stdout, collected by Alloy (`loki.source.docker`) so requests show up in Loki next to backend logs. `stub_status` on an internal-only port for request/connection metrics.

## Consequences

**Positive**

- `docker compose --profile all up -d` gives a working app at one URL, including the web UI.
- Same-origin SPA + API: no CORS for the main client, cookies possible later.
- Only one port exposed; databases and observability are no longer on the host by default.
- The same layout moves to the home server by changing `.env`.

**Negative**

- The web app is now built in Docker; `pnpm dev` stays the way to work on the UI with hot reload.
- One more image to build in CI (add a build job; push later with a registry).
- `docker-compose.ports.yml` must be added explicitly when a host port is needed.

## Alternatives considered

- **Traefik or Caddy.** Caddy gives automatic HTTPS, Traefik discovers containers by labels. Both fine; nginx chosen because the plan already names it (§2), it is the most common in job requirements, and the config is explicit, which suits learning.
- **Spring Boot serving the SPA from `static/`.** Rejected: couples frontend releases to backend releases and mixes static caching into the API server.
- **Keep published ports, no proxy.** Rejected: no single URL, CORS stays, Grafana and Postgres exposed.

## Rollout

1. Web Dockerfile + nginx image + config, `APP_HOST` / `GRAFANA_HOST` in `.env.example`.
2. Compose: nginx service; remove host ports from backend, Postgres and observability (move them to `docker-compose.ports.yml`).
3. Backend: forwarded headers, single CORS config from properties.
4. Grafana: root URL, login on, admin password.
5. CI: build the nginx image on PRs.
6. README: "Open http://finance.localhost".
