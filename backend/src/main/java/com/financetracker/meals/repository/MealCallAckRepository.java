package com.financetracker.meals.repository;

import com.financetracker.meals.model.MealCallAck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MealCallAckRepository extends JpaRepository<MealCallAck, UUID> {

    List<MealCallAck> findByMealCallId(UUID mealCallId);

    Optional<MealCallAck> findByMealCallIdAndUserId(UUID mealCallId, UUID userId);
}
