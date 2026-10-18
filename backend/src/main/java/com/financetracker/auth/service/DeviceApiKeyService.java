package com.financetracker.auth.service;

import com.financetracker.access.model.Permission;
import com.financetracker.access.service.AccessService;
import com.financetracker.auth.model.DeviceApiKey;
import com.financetracker.auth.model.User;
import com.financetracker.auth.repository.DeviceApiKeyRepository;
import com.financetracker.auth.repository.UserRepository;
import com.financetracker.auth.security.AuthenticatedUser;
import com.financetracker.common.exception.EntityNotFoundException;
import com.financetracker.common.security.SecretTokens;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Device API keys (ADR-016). A key's raw value is returned once at creation and never stored; only its SHA-256
 * hash is kept, looked up by hashing the presented value. A key authenticates the request as its owning user, but
 * only ever with the device-scoped, data-plane permissions in {@link #DEVICE_PERMISSIONS} — admin and management
 * permissions are never granted to a device, so a device key can't reach {@code /api/v1/admin/**} even when the
 * owner is an admin. Keys are created and revoked by an admin; a revoked or unknown key authenticates no one.
 */
@Service
@RequiredArgsConstructor
public class DeviceApiKeyService {

    /** A device key is {@code ftk_} followed by a URL-safe random token. */
    public static final String KEY_PREFIX = "ftk_";

    /**
     * What a device may do, regardless of its owner's role. Only the owner's own effective permissions among
     * these are granted; every administration/management permission is excluded on purpose.
     */
    static final Set<Permission> DEVICE_PERMISSIONS = EnumSet.of(
        Permission.TRANSACTION_READ,
        Permission.TRANSACTION_WRITE,
        Permission.DASHBOARD_VIEW,
        Permission.CSV_IMPORT,
        Permission.RECEIPT_SCAN,
        Permission.CATEGORY_READ,
        Permission.CATEGORY_WRITE,
        Permission.BUDGETS);

    /** {@code last_used_at} is refreshed at most this often, so authentication doesn't write on every request. */
    static final Duration TOUCH_INTERVAL = Duration.ofMinutes(1);

    private final DeviceApiKeyRepository keys;
    private final UserRepository users;
    private final AccessService accessService;

    /**
     * Creates a key for a device owned by {@code ownerId}. Returns the raw key (shown once) with the stored row's
     * id and the owner's email; the raw key is not kept.
     *
     * @throws EntityNotFoundException when the owner does not exist
     */
    @Transactional
    public Created create(UUID ownerId, String label, UUID createdBy) {
        User owner = users.findById(ownerId)
            .orElseThrow(() -> new EntityNotFoundException("User not found: " + ownerId));
        String raw = KEY_PREFIX + SecretTokens.newToken();
        DeviceApiKey key = keys.save(DeviceApiKey.builder()
            .userId(owner.getId())
            .label(label.strip())
            .keyHash(SecretTokens.hash(raw))
            .createdAt(Instant.now())
            .createdBy(createdBy)
            .build());
        return new Created(key.getId(), raw, owner.getId(), owner.getEmail(), key.getLabel());
    }

    /** Every key, newest first, with its owner's current email; never the raw key (only its hash is stored). */
    @Transactional(readOnly = true)
    public List<Summary> list() {
        List<DeviceApiKey> all = keys.findAllByOrderByCreatedAtDesc();
        Set<UUID> ownerIds = all.stream().map(DeviceApiKey::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        var emails = users.findAllById(ownerIds).stream().collect(Collectors.toMap(User::getId, User::getEmail));
        return all.stream()
            .map(key -> new Summary(key.getId(), key.getLabel(), key.getUserId(), emails.get(key.getUserId()),
                key.getCreatedAt(), key.getLastUsedAt(), key.getRevokedAt()))
            .toList();
    }

    /** Revokes a key so it stops authenticating. Idempotent: an already-revoked key keeps its original time. */
    @Transactional
    public void revoke(UUID id) {
        DeviceApiKey key = keys.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Device key not found: " + id));
        if (!key.isRevoked()) {
            key.setRevokedAt(Instant.now());
            keys.save(key);
        }
    }

    /**
     * Authenticates a presented raw key as its owning user with the device-scoped permissions. Empty when the key
     * is blank, unknown, revoked, or its owner is missing or disabled. Refreshes {@code last_used_at} at most once
     * a minute on success.
     */
    @Transactional
    public Optional<AuthenticatedUser> authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        return keys.findByKeyHash(SecretTokens.hash(rawKey.strip()))
            .filter(key -> !key.isRevoked())
            .flatMap(key -> users.findById(key.getUserId())
                .filter(User::isEnabled)
                .map(owner -> {
                    touch(key);
                    return new AuthenticatedUser(owner, devicePermissions(owner));
                }));
    }

    /** The owner's effective permissions narrowed to what a device may exercise. */
    private Set<Permission> devicePermissions(User owner) {
        Set<Permission> granted = EnumSet.noneOf(Permission.class);
        granted.addAll(accessService.effectivePermissions(owner.getId(), owner.getRole()));
        granted.retainAll(DEVICE_PERMISSIONS);
        return granted;
    }

    private void touch(DeviceApiKey key) {
        Instant now = Instant.now();
        if (key.getLastUsedAt() == null || key.getLastUsedAt().isBefore(now.minus(TOUCH_INTERVAL))) {
            key.setLastUsedAt(now);
            keys.save(key);
        }
    }

    /** A freshly created key: its stored id, the owner, and the one-time raw key. */
    public record Created(UUID id, String key, UUID userId, String ownerEmail, String label) { }

    /** A key as the admin list shows it; never the raw key. */
    public record Summary(UUID id, String label, UUID userId, String ownerEmail,
                          Instant createdAt, Instant lastUsedAt, Instant revokedAt) { }
}
