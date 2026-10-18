package com.financetracker.access.repository;

import com.financetracker.access.model.Permission;
import com.financetracker.access.model.UserFeatureOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserFeatureOverrideRepository extends JpaRepository<UserFeatureOverride, UUID> {

    List<UserFeatureOverride> findByUserId(UUID userId);

    Optional<UserFeatureOverride> findByUserIdAndPermission(UUID userId, Permission permission);
}
