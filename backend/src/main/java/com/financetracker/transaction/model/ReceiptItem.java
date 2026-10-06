package com.financetracker.transaction.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "receipt_items", indexes = {
    @Index(name = "idx_receipt_items_transaction", columnList = "transactionId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceiptItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "item_description", nullable = false)
    private String itemDescription;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
}
