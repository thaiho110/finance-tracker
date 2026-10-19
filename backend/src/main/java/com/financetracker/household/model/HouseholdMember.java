package com.financetracker.household.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * A user's membership of a household (ADR-022). {@code userId} is unique, so a user is in at most one
 * household. Flat membership — there is no owner/admin role in this ADR.
 */
@Entity
@Table(name = "household_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HouseholdMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;
}
