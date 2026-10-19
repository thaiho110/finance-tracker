package com.financetracker.meals.repository;

import com.financetracker.meals.model.MealCall;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MealCallRepository extends JpaRepository<MealCall, UUID> {

    List<MealCall> findByHouseholdIdOrderByCreatedAtDesc(UUID householdId, Pageable pageable);
}
