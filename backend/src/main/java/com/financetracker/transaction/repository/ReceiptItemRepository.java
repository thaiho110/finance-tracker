package com.financetracker.transaction.repository;

import com.financetracker.transaction.model.ReceiptItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReceiptItemRepository extends JpaRepository<ReceiptItem, UUID> {
    List<ReceiptItem> findByTransactionId(UUID transactionId);
    void deleteByTransactionId(UUID transactionId);
}
