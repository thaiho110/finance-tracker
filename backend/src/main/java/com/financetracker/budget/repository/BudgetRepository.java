package com.financetracker.budget.repository;

import com.financetracker.budget.model.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    /** Every budget change of the user up to a month, oldest first (a user has at most a few hundred). */
    @Query("SELECT b FROM Budget b WHERE b.userId = :userId AND b.effectiveFrom <= :month ORDER BY b.effectiveFrom")
    List<Budget> findUpTo(@Param("userId") UUID userId, @Param("month") LocalDate month);

    @Query("SELECT b FROM Budget b WHERE b.userId = :userId AND b.effectiveFrom = :month "
        + "AND ((:categoryId IS NULL AND b.categoryId IS NULL) OR b.categoryId = :categoryId)")
    Optional<Budget> findExact(@Param("userId") UUID userId, @Param("categoryId") UUID categoryId,
                               @Param("month") LocalDate month);

    @Query("SELECT DISTINCT b.userId FROM Budget b WHERE b.categoryId = :categoryId")
    List<UUID> findUsersWithBudgetOn(@Param("categoryId") UUID categoryId);
}
