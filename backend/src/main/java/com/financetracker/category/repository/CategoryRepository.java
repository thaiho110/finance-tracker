package com.financetracker.category.repository;

import com.financetracker.category.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    /** System categories plus the user's own, archived included. Hidden system categories are filtered by the caller. */
    @Query("SELECT c FROM Category c WHERE c.userId IS NULL OR c.userId = :userId ORDER BY c.name")
    List<Category> findVisibleTo(@Param("userId") UUID userId);

    @Query("SELECT c FROM Category c WHERE c.userId IS NULL ORDER BY c.name")
    List<Category> findSystem();

    @Query("SELECT c FROM Category c WHERE c.userId IS NULL AND lower(c.name) = lower(:name)")
    Optional<Category> findSystemByName(@Param("name") String name);

    @Query("SELECT c FROM Category c WHERE c.userId = :userId AND lower(c.name) = lower(:name)")
    Optional<Category> findOwnByName(@Param("userId") UUID userId, @Param("name") String name);

    List<Category> findByIdIn(Collection<UUID> ids);

    @Query(value = "SELECT category_id FROM user_hidden_categories WHERE user_id = :userId", nativeQuery = true)
    Set<UUID> findHiddenIds(@Param("userId") UUID userId);

    @Modifying
    @Query(value = "INSERT INTO user_hidden_categories (user_id, category_id) VALUES (:userId, :categoryId) "
        + "ON CONFLICT DO NOTHING", nativeQuery = true)
    void hide(@Param("userId") UUID userId, @Param("categoryId") UUID categoryId);

    @Modifying
    @Query(value = "DELETE FROM user_hidden_categories WHERE user_id = :userId AND category_id = :categoryId",
        nativeQuery = true)
    void unhide(@Param("userId") UUID userId, @Param("categoryId") UUID categoryId);
}
