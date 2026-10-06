package com.financetracker.transaction.repository;

import com.financetracker.transaction.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findByCreatedBy(UUID createdBy, Pageable pageable);

    Page<Transaction> findByCreatedByAndCategory(UUID createdBy, String category, Pageable pageable);

    @Query("""
        SELECT t FROM Transaction t
        WHERE t.createdBy = :createdBy
        AND (:dateFrom IS NULL OR t.date >= :dateFrom)
        AND (:dateTo IS NULL OR t.date <= :dateTo)
        AND (:category IS NULL OR t.category = :category)
        ORDER BY t.date DESC
        """)
    Page<Transaction> findByFilters(@Param("createdBy") UUID createdBy,
                                    @Param("category") String category,
                                    @Param("dateFrom") LocalDate dateFrom,
                                    @Param("dateTo") LocalDate dateTo,
                                    Pageable pageable);

    @Query("SELECT COUNT(t) > 0 FROM Transaction t WHERE t.createdBy = :createdBy " +
           "AND t.date = :date AND t.amount = :amount " +
           "AND t.createdAt > :since")
    boolean existsDuplicate(@Param("createdBy") UUID createdBy,
                            @Param("date") LocalDate date,
                            @Param("amount") BigDecimal amount,
                            @Param("since") java.time.Instant since);

    @Query("SELECT DISTINCT t.category FROM Transaction t WHERE t.createdBy = :createdBy ORDER BY t.category")
    List<String> findDistinctCategoriesByCreatedBy(@Param("createdBy") UUID createdBy);

    boolean existsByIdAndCreatedBy(UUID id, UUID createdBy);
}
