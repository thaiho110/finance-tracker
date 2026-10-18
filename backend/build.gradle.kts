plugins {
    java
    id("org.springframework.boot") version "4.0.8"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.flywaydb.flyway") version "10.20.0"
    id("checkstyle")
    id("pmd")
    id("com.github.spotbugs") version "6.1.7"
    jacoco
}

group = "com.financetracker"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Database
    runtimeOnly("org.postgresql:postgresql")

    // Flyway
    // Spring Boot 4 moved Flyway's auto-configuration into its own module: without this starter
    // flyway-core sits on the classpath but migrations never run.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")

    // CSV Parsing
    implementation("org.apache.commons:commons-csv:1.11.0")

    // PDF Processing — render PDF pages to images for OCR
    implementation("org.apache.pdfbox:pdfbox:3.0.3")

    // JWT — using Gson serializer to avoid Jackson 2/3 classpath conflict
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-gson:0.11.5")

    // OpenAPI / Swagger
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")

    // Observability — OpenTelemetry bridge for Micrometer
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")

    // Actuator Micrometer + Prometheus
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Rate Limiting — Token Bucket (Bucket4j)
    implementation("com.bucket4j:bucket4j-core:8.10.1")
    implementation("com.github.ben-manes.caffeine:caffeine")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation("org.springframework.boot:spring-boot-restclient")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
}

tasks.withType<Test> {
    useJUnitPlatform()
    maxParallelForks = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    finalizedBy(tasks.jacocoTestReport)
}

// Fast unit tests run in parallel forks; the Testcontainers integration tests run in their own task in a
// single fork (one shared Postgres container, and no parallel Docker-environment discovery races).
tasks.named<Test>("test") {
    filter { excludeTestsMatching("com.financetracker.integration.*") }
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Testcontainers integration tests (real PostgreSQL + Flyway), run serially."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("com.financetracker.integration.*") }
    shouldRunAfter(tasks.test)
}

// Applied last so it wins over the withType defaults above.
tasks.named<Test>("integrationTest") {
    maxParallelForks = 1
    setFinalizedBy(emptyList<Any>())
}

tasks.named("check") {
    dependsOn(integrationTest)
}

// ── JaCoCo ──
jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit {
                minimum = "0.0".toBigDecimal() // Informational only; toggle via CI
            }
        }
    }
}

// ── Checkstyle ──
checkstyle {
    configFile = file("${rootDir}/config/checkstyle/checkstyle.xml")
    toolVersion = "10.21.4"
    maxErrors = 0
    maxWarnings = 0
}

// ── PMD ──
pmd {
    ruleSetFiles = files("${rootDir}/config/pmd/pmd-ruleset.xml")
    toolVersion = "7.11.0"
    rulesMinimumPriority = 5
}

// ── SpotBugs ──
spotbugs {
    toolVersion = "4.9.3"
    excludeFilter = file("${rootDir}/config/spotbugs/spotbugs-exclude.xml")
}
