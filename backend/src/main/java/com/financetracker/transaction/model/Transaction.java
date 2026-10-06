package com.financetracker.transaction.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_transactions_date", columnList = "date"),
    @Index(name = "idx_transactions_category", columnList = "category"),
    @Index(name = "idx_transactions_merchant", columnList = "cleanMerchant"),
    @Index(name = "idx_transactions_created_by", columnList = "createdBy")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "raw_description", nullable = false, length = 500)
    private String rawDescription;

    @Column(name = "clean_merchant", nullable = false)
    private String cleanMerchant;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 10)
    private SourceType sourceType;

    @Column(name = "client_id", length = 100)
    private String clientId;

    @Column(name = "is_duplicate", nullable = false)
    @Builder.Default
    private boolean isDuplicate = false;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
