package com.financetracker.auth.repository;

import com.financetracker.auth.model.DeviceApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceApiKeyRepository extends JpaRepository<DeviceApiKey, UUID> {

    Optional<DeviceApiKey> findByKeyHash(String keyHash);

    List<DeviceApiKey> findAllByOrderByCreatedAtDesc();
}
