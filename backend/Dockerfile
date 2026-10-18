FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY src src
RUN ./gradlew bootJar --no-daemon

# Runtime: Ubuntu-based JRE so receipt OCR can use the distro's Tesseract 5 packages (ADR-014).
FROM eclipse-temurin:21-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng tesseract-ocr-vie \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /app

# OpenTelemetry Java agent, pinned so a new release can't change behaviour between builds
ARG OTEL_AGENT_VERSION=2.32.0
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
# Agent settings come from OTEL_* environment variables (docker-compose.yml). -D flags here would
# override them, so keep this line free of otel.* properties.
ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "app.jar"]
