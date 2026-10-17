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

# Download OpenTelemetry Java Agent
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", \
    "-javaagent:/app/opentelemetry-javaagent.jar", \
    "-Dotel.service.name=finance-tracker-backend", \
    "-Dotel.exporter.otlp.endpoint=http://alloy:4317", \
    "-Dotel.metrics.exporter=otlp", \
    "-Dotel.traces.exporter=otlp", \
    "-Dotel.logs.exporter=otlp", \
    "-jar", "app.jar"]
