package com.financetracker.auth.repository;

import com.financetracker.auth.model.RegistrationInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegistrationInviteRepository extends JpaRepository<RegistrationInvite, UUID> {

    Optional<RegistrationInvite> findByTokenHash(String tokenHash);

    List<RegistrationInvite> findAllByOrderByCreatedAtDesc();
}
