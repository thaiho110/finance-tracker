package com.financetracker.admin.service;

import com.financetracker.admin.dto.AuditEntryResponse;
import com.financetracker.admin.model.AuditAction;
import com.financetracker.admin.model.AuditEntry;
import com.financetracker.admin.repository.AuditEntryRepository;
import com.financetracker.auth.model.User;
import com.financetracker.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Writes and reads the audit log of administrative changes. */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEntryRepository repository;
    private final UserRepository userRepository;

    /**
     * Records one change. Runs in the caller's transaction, so the entry is only kept if the change is.
     *
     * @param actorId who made the change, or null when the application made it
     */
    @Transactional
    public void record(UUID actorId, AuditAction action, String target, String details) {
        repository.save(new AuditEntry(actorId, action, target, details));
    }

    /** Newest first, with the actor's current email. */
    @Transactional(readOnly = true)
    public Page<AuditEntryResponse> list(Pageable pageable) {
        Page<AuditEntry> page = repository.findAllByOrderByCreatedAtDesc(pageable);
        Set<UUID> actorIds = page.stream().map(AuditEntry::getActorId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> emails = userRepository.findAllById(actorIds).stream()
            .collect(Collectors.toMap(User::getId, User::getEmail));
        return page.map(entry -> new AuditEntryResponse(entry.getId(), entry.getActorId(),
            entry.getActorId() == null ? null : emails.get(entry.getActorId()),
            entry.getAction().name(), entry.getTarget(), entry.getDetails(), entry.getCreatedAt()));
    }
}
