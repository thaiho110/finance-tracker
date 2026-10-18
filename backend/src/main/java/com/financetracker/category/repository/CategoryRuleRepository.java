package com.financetracker.category.repository;

import com.financetracker.category.model.CategoryRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRuleRepository extends JpaRepository<CategoryRule, UUID> {

    @Query("SELECT r FROM CategoryRule r WHERE r.userId IS NULL ORDER BY r.keyword")
    List<CategoryRule> findSystem();

    List<CategoryRule> findByUserIdOrderByKeyword(UUID userId);

    @Query("SELECT r FROM CategoryRule r WHERE r.userId IS NULL AND r.keyword = :keyword")
    Optional<CategoryRule> findSystemByKeyword(@Param("keyword") String keyword);

    Optional<CategoryRule> findByUserIdAndKeyword(UUID userId, String keyword);
}
