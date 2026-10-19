package com.financetracker.household.service;

import com.financetracker.auth.model.User;
import com.financetracker.auth.repository.UserRepository;
import com.financetracker.common.exception.ConflictException;
import com.financetracker.common.exception.EntityNotFoundException;
import com.financetracker.common.security.SecretTokens;
import com.financetracker.household.dto.HouseholdView;
import com.financetracker.household.dto.MemberView;
import com.financetracker.household.model.Household;
import com.financetracker.household.model.HouseholdInvite;
import com.financetracker.household.model.HouseholdMember;
import com.financetracker.household.model.InviteStatus;
import com.financetracker.household.repository.HouseholdInviteRepository;
import com.financetracker.household.repository.HouseholdMemberRepository;
import com.financetracker.household.repository.HouseholdRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Households (ADR-022): create, invite, join, leave. A user is in at most one household; membership grants
 * addressing, never access to another member's data. Platform and always on — no module gates it.
 */
@Service
@RequiredArgsConstructor
public class HouseholdService {

    /** How long a fresh invite stays usable. */
    private static final Duration INVITE_TTL = Duration.ofDays(7);

    private final HouseholdRepository households;
    private final HouseholdMemberRepository members;
    private final HouseholdInviteRepository invites;
    private final UserRepository users;

    /** The caller's household with its members, or empty if they are not in one. */
    @Transactional(readOnly = true)
    public Optional<HouseholdView> viewOf(UUID userId) {
        return members.findByUserId(userId)
            .map(member -> toView(households.findById(member.getHouseholdId())
                .orElseThrow(() -> new IllegalStateException("Membership points at a missing household"))));
    }

    /** The id of the household the user belongs to, or empty if none. For features that address the household. */
    @Transactional(readOnly = true)
    public Optional<UUID> householdIdOf(UUID userId) {
        return members.findByUserId(userId).map(HouseholdMember::getHouseholdId);
    }

    /** The user ids of every member of a household. */
    @Transactional(readOnly = true)
    public List<UUID> memberUserIds(UUID householdId) {
        return members.findByHouseholdId(householdId).stream().map(HouseholdMember::getUserId).toList();
    }

    /** Create a household and make the caller its first member. */
    @Transactional
    public HouseholdView create(UUID userId, String name) {
        requireNoHousehold(userId);
        Household household = households.save(Household.builder().name(name).createdBy(userId).build());
        members.save(HouseholdMember.builder()
            .householdId(household.getId()).userId(userId).joinedAt(Instant.now()).build());
        return toView(household);
    }

    /** Create a shareable invite to the caller's household. The caller must be a member. */
    @Transactional
    public HouseholdInvite invite(UUID userId, String email) {
        HouseholdMember membership = members.findByUserId(userId)
            .orElseThrow(() -> new ConflictException("You must be in a household to invite someone"));
        return invites.save(HouseholdInvite.builder()
            .householdId(membership.getHouseholdId())
            .inviteeEmail(email)
            .code(SecretTokens.newToken())
            .status(InviteStatus.PENDING)
            .invitedBy(userId)
            .expiresAt(Instant.now().plus(INVITE_TTL))
            .build());
    }

    /** Accept an invite by its code and join that household. */
    @Transactional
    public HouseholdView join(UUID userId, String code) {
        HouseholdInvite invite = invites.findByCode(code)
            .filter(candidate -> candidate.getStatus() == InviteStatus.PENDING)
            .orElseThrow(() -> new EntityNotFoundException("Invite not found or no longer valid"));
        if (invite.getExpiresAt().isBefore(Instant.now())) {
            invite.setStatus(InviteStatus.EXPIRED);
            invites.save(invite);
            throw new EntityNotFoundException("Invite not found or no longer valid");
        }
        requireNoHousehold(userId);
        members.save(HouseholdMember.builder()
            .householdId(invite.getHouseholdId()).userId(userId).joinedAt(Instant.now()).build());
        invite.setStatus(InviteStatus.ACCEPTED);
        invites.save(invite);
        return toView(households.findById(invite.getHouseholdId())
            .orElseThrow(() -> new IllegalStateException("Invite points at a missing household")));
    }

    /** The caller leaves their household; the now-empty household (and its invites) is deleted. */
    @Transactional
    public void leave(UUID userId) {
        HouseholdMember membership = members.findByUserId(userId)
            .orElseThrow(() -> new EntityNotFoundException("You are not in a household"));
        UUID householdId = membership.getHouseholdId();
        members.delete(membership);
        if (members.countByHouseholdId(householdId) == 0) {
            households.deleteById(householdId); // invites cascade via FK ON DELETE CASCADE
        }
    }

    private void requireNoHousehold(UUID userId) {
        if (members.findByUserId(userId).isPresent()) {
            throw new ConflictException("You are already in a household");
        }
    }

    private HouseholdView toView(Household household) {
        List<MemberView> memberViews = members.findByHouseholdId(household.getId()).stream()
            .map(member -> new MemberView(member.getUserId(), emailOf(member.getUserId()), member.getJoinedAt()))
            .toList();
        return new HouseholdView(household.getId(), household.getName(), household.getCreatedAt(), memberViews);
    }

    private String emailOf(UUID userId) {
        return users.findById(userId).map(User::getEmail).orElse(null);
    }
}
