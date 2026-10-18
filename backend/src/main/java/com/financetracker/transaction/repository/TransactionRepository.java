package com.financetracker.transaction.repository;

import com.financetracker.transaction.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdAndCreatedBy(UUID id, UUID createdBy);

    @Query("""
        SELECT t FROM Transaction t
        WHERE t.createdBy = :createdBy
        AND (:dateFrom IS NULL OR t.date >= :dateFrom)
        AND (:dateTo IS NULL OR t.date <= :dateTo)
        AND (:categoryId IS NULL OR t.categoryId = :categoryId)
        ORDER BY t.date DESC
        """)
    Page<Transaction> findByFilters(@Param("createdBy") UUID createdBy,
                                    @Param("categoryId") UUID categoryId,
                                    @Param("dateFrom") LocalDate dateFrom,
                                    @Param("dateTo") LocalDate dateTo,
                                    Pageable pageable);

    @Query("SELECT COUNT(t) > 0 FROM Transaction t WHERE t.createdBy = :createdBy "
           + "AND t.amount = :amount AND t.date BETWEEN :dateFrom AND :dateTo")
    boolean existsDuplicate(@Param("createdBy") UUID createdBy,
                            @Param("dateFrom") LocalDate dateFrom,
                            @Param("dateTo") LocalDate dateTo,
                            @Param("amount") BigDecimal amount);

    long countByCreatedBy(UUID createdBy);

    @Query(value = """
        SELECT c.id, c.name, c.kind, c.color, SUM(t.amount) AS total
        FROM transactions t
        JOIN categories c ON c.id = t.category_id
        WHERE t.created_by = :createdBy AND c.kind <> 'TRANSFER'
        GROUP BY c.id, c.name, c.kind, c.color
        ORDER BY ABS(SUM(t.amount)) DESC
        """, nativeQuery = true)
    List<Object[]> categoryBreakdownNative(@Param("createdBy") UUID createdBy);

    @Query(value = """
        SELECT TO_CHAR(t.date, 'YYYY-MM') AS month, SUM(t.amount) AS total
        FROM transactions t
        JOIN categories c ON c.id = t.category_id
        WHERE t.created_by = :createdBy AND c.kind = 'EXPENSE'
        GROUP BY TO_CHAR(t.date, 'YYYY-MM')
        ORDER BY month
        """, nativeQuery = true)
    List<Object[]> monthlyTrendNative(@Param("createdBy") UUID createdBy);

    @Query("""
        SELECT t FROM Transaction t
        WHERE t.createdBy = :createdBy
        ORDER BY t.date DESC, t.createdAt DESC
        """)
    List<Transaction> findRecentByCreatedBy(@Param("createdBy") UUID createdBy, Pageable pageable);

    long countByCategoryId(UUID categoryId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Transaction t SET t.categoryId = :to WHERE t.createdBy = :createdBy AND t.categoryId = :from")
    int reassignCategory(@Param("createdBy") UUID createdBy, @Param("from") UUID from, @Param("to") UUID to);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Transaction t SET t.categoryId = :to WHERE t.createdBy = :createdBy AND t.id IN :ids")
    int setCategory(@Param("createdBy") UUID createdBy, @Param("ids") Collection<UUID> ids, @Param("to") UUID to);

    /** Id, clean merchant and raw description of the user's transactions in one category. */
    @Query("SELECT t.id, t.cleanMerchant, t.rawDescription FROM Transaction t "
        + "WHERE t.createdBy = :createdBy AND t.categoryId = :categoryId")
    List<Object[]> findMerchantsInCategory(@Param("createdBy") UUID createdBy, @Param("categoryId") UUID categoryId);
}
