package com.financetracker.category.repository;

import com.financetracker.category.model.CategoryMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryMappingRepository extends JpaRepository<CategoryMapping, UUID> {
    Optional<CategoryMapping> findByKeyword(String keyword);

    @Query("SELECT DISTINCT cm.category FROM CategoryMapping cm ORDER BY cm.category")
    List<String> findDistinctCategories();

    @Query("SELECT cm.keyword FROM CategoryMapping cm ORDER BY LENGTH(cm.keyword) DESC")
    List<String> findAllKeywords();
}
