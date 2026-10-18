# ADR-008: OpenTelemetry + Grafana Observability Stack

## Status
Accepted

## Context
The Finance Tracker needs production-grade observability to:
- Debug performance issues (slow CSV parsing, OCR latency, slow DB queries)
- Monitor system health in production
- Track business metrics (transactions imported, OCR success rate, duplicates)
- Trace requests end-to-end across API → service → database → external API calls
- Unify logs, metrics, and traces in a single view

Key forces:
- Spring Boot 3.x has first-class Micrometer + OpenTelemetry support
- Team wants an open, standards-based approach (OpenTelemetry)
- Need a single collector to receive, process, and route telemetry data
- Must self-host on a single VM — cannot use cloud-managed observability backends

## Decision
Use the **OpenTelemetry standard** with **Grafana Alloy** as the collector, and **Grafana LGTM stack** (Loki, Grafana, Tempo, Mimir/Prometheus) for storage and visualization.

### Architecture

```
Spring Boot App (OTel Java Agent)
        │
        │ OTLP (gRPC) :4317
        ▼
   Grafana Alloy (Collector)
        │
        ├────▶ Prometheus (Metrics)
        ├────▶ Tempo (Traces)
        └────▶ Loki (Logs)
                  │
                  ▼
              Grafana (Dashboards)
```

### Instrumentation Strategy

| Layer | What's Instrumented | How |
|-------|---------------------|-----|
| **HTTP** | Incoming requests (duration, status, path) | OTel Java Agent auto-instrumentation |
| **JDBC** | All database queries (query text, duration) | OTel JDBC instrumentation |
| **Outbound HTTP** | Vision API calls (latency, response size) | OTel HTTP Client instrumentation |
| **JVM** | Heap, GC, threads, CPU, file descriptors | Micrometer JVM metrics |
| **Business** | Custom counters (rows parsed, OCR success, duplicates found) | Micrometer `@Counted` / `MeterRegistry` |
| **Logs** | Application logs with trace ID correlation | Logback + OTLP appender |

## Consequences

### Positive
- **Standard protocol** — OpenTelemetry is the industry standard; no vendor lock-in
- **Auto-instrumentation** — OTel Java Agent instruments HTTP, JDBC, gRPC, etc. with zero code changes:
  ```bash
  java -javaagent:opentelemetry-javaagent.jar \
       -Dotel.service.name=finance-tracker-backend \
       -Dotel.exporter.otlp.endpoint=http://alloy:4317 \
       -jar backend.jar
  ```
- **Single collector** — Alloy replaces the OpenTelemetry Collector with a simpler configuration
- **Unified view** — Grafana correlates logs (Loki) + metrics (Prometheus) + traces (Tempo)
- **Trace ID in logs** — Every log line includes `traceId` and `spanId` for correlation
- **Self-hosted** — Entire stack runs in Docker Compose on a single VM
- **Business metrics** — Custom counters give insight into domain KPIs:
  ```java
  @Service
  public class MetricsService {
      private final Counter csvRowsParsed;
      private final Counter ocrSuccessCount;
      private final Counter duplicatesFound;

      public MetricsService(MeterRegistry registry) {
          this.csvRowsParsed = Counter.builder("finance.csv.rows.parsed")
              .description("Total CSV rows parsed").register(registry);
          this.ocrSuccessCount = Counter.builder("finance.ocr.success")
              .description("Successful OCR extractions").register(registry);
          this.duplicatesFound = Counter.builder("finance.duplicates.found")
              .description("Duplicate transactions detected").register(registry);
      }
  }
  ```

### Negative
- **Resource overhead** — Observability stack adds ~2GB RAM (Prometheus + Loki + Tempo + Grafana + Alloy)
- **Configuration complexity** — 5 separate services to configure and maintain
- **Storage growth** — Prometheus and Loki metrics/logs grow unbounded without retention policies
- **Startup time** — Docker Compose takes longer to start with all observability services
- **Learning curve** — Team must learn Alloy config language, PromQL, LogQL, TraceQL

### Neutral
- Alloy configuration is declarative and can be version-controlled with the project
- Dashboards can be provisioned automatically via Grafana's provisioning API
- Tempo can be replaced with Mimir if long-term trace storage is needed

## Alternatives Considered

**Datadog / New Relic / Grafana Cloud**
- Rejected: Monthly per-host cost ($15–$70/month) for a personal project. Self-hosted is free.

**OpenTelemetry Collector (otelcol) instead of Alloy**
- Considered: More mature, but Alloy is Grafana's recommended collector with tighter Grafana integration and simpler config syntax.

**ELK Stack (Elasticsearch + Logstash + Kibana)**
- Rejected: Heavy (Elasticsearch JVM), no native tracing support, no OTLP pipeline.

**Jaeger (instead of Tempo)**
- Considered: Mature, simpler. Tempo chosen for native Grafana integration and cheaper storage (object storage backend).

**VictoriaMetrics (instead of Prometheus)**
- Considered: Better long-term storage, but Prometheus is simpler for this scale and integrates directly with Grafana.

## Alloy Configuration (config.alloy)

```alloy
// Receive OTLP data from Spring Boot
otelcol.receiver.otlp "default" {
  grpc {
    endpoint = "0.0.0.0:4317"
  }
  http {
    endpoint = "0.0.0.0:4318"
  }
}

// Batch before exporting
otelcol.processor.batch "default" {
  timeout = "1s"
  send_batch_size = 1024
}

// Export to Prometheus, Tempo, Loki
otelcol.exporter.prometheus "default" {
  forward_to = [prometheus.remote_write.default.receiver]
}

prometheus.remote_write "default" {
  endpoint {
    url = "http://prometheus:9090/api/v1/write"
  }
}

otelcol.exporter.otlp "tempo" {
  client {
    endpoint = "tempo:4317"
    tls {
      insecure = true
    }
  }
}

otelcol.exporter.loki "default" {
  forward_to = [loki.write.default.receiver]
}

loki.write "default" {
  endpoint {
    url = "http://loki:3100/loki/api/v1/push"
  }
}

// Pipeline
otelcol.pipeline "default" {
  receiver  = otelcol.receiver.otlp.default
  processor = [otelcol.processor.batch.default]
  exporter  = [
    otelcol.exporter.prometheus.default,
    otelcol.exporter.otlp.tempo,
    otelcol.exporter.loki.default,
  ]
}
```

## References
- [Observability Architecture](../README.md#4-observability-architecture)
- [OpenTelemetry Java Agent](https://opentelemetry.io/docs/languages/java/agent/)
- [Grafana Alloy Documentation](https://grafana.com/docs/alloy/latest/)
- [Grafana LGTM Stack](https://grafana.com/go/grafanacon/grafana-lgtm-stack/)
