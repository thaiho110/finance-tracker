package com.financetracker.access.repository;

import com.financetracker.access.model.Module;
import com.financetracker.access.model.UserModulePreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserModulePreferenceRepository extends JpaRepository<UserModulePreference, UUID> {

    List<UserModulePreference> findByUserId(UUID userId);

    Optional<UserModulePreference> findByUserIdAndModule(UUID userId, Module module);
}
