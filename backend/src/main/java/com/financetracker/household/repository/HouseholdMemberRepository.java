package com.financetracker.household.repository;

import com.financetracker.household.model.HouseholdMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, UUID> {

    Optional<HouseholdMember> findByUserId(UUID userId);

    List<HouseholdMember> findByHouseholdId(UUID householdId);

    long countByHouseholdId(UUID householdId);
}
