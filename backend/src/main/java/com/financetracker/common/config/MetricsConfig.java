package com.financetracker.common.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.metrics.enabled", havingValue = "true", matchIfMissing = true)
public class MetricsConfig {

    @Bean
    public Counter csvRowsParsed(MeterRegistry registry) {
        return Counter.builder("finance.csv.rows.parsed")
            .description("Total CSV rows parsed successfully").register(registry);
    }

    @Bean
    public Counter csvFilesProcessed(MeterRegistry registry) {
        return Counter.builder("finance.csv.files.processed")
            .description("Total CSV files processed").register(registry);
    }

    @Bean
    public Counter ocrRequestsTotal(MeterRegistry registry) {
        return Counter.builder("finance.ocr.requests.total")
            .description("Total OCR requests made").register(registry);
    }

    @Bean
    public Counter ocrSuccessCount(MeterRegistry registry) {
        return Counter.builder("finance.ocr.success")
            .description("Successful OCR extractions").register(registry);
    }

    @Bean
    public Counter ocrFailureCount(MeterRegistry registry) {
        return Counter.builder("finance.ocr.failures")
            .description("Failed OCR extractions").register(registry);
    }

    @Bean
    public Counter duplicatesFound(MeterRegistry registry) {
        return Counter.builder("finance.duplicates.found")
            .description("Duplicate transactions detected").register(registry);
    }

    @Bean
    public Counter transactionsCreated(MeterRegistry registry) {
        return Counter.builder("finance.transactions.created")
            .description("Total transactions created").register(registry);
    }
}
