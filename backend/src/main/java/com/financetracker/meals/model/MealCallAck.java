package com.financetracker.meals.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One member's answer to a meal call (ADR-025 §3). One row per member per call; a later answer replaces the
 * earlier one.
 */
@Entity
@Table(name = "meal_call_acks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MealCallAck {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "meal_call_id", nullable = false)
    private UUID mealCallId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MealResponse response;

    @Column(name = "responded_at", nullable = false)
    private Instant respondedAt;
}
