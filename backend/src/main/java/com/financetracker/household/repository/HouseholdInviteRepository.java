package com.financetracker.household.repository;

import com.financetracker.household.model.HouseholdInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface HouseholdInviteRepository extends JpaRepository<HouseholdInvite, UUID> {

    Optional<HouseholdInvite> findByCode(String code);
}
