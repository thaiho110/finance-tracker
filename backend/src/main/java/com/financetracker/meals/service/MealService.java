package com.financetracker.meals.service;

import com.financetracker.common.exception.ConflictException;
import com.financetracker.common.exception.EntityNotFoundException;
import com.financetracker.household.service.HouseholdService;
import com.financetracker.meals.model.MealCall;
import com.financetracker.meals.model.MealCallAck;
import com.financetracker.meals.model.MealResponse;
import com.financetracker.meals.repository.MealCallAckRepository;
import com.financetracker.meals.repository.MealCallRepository;
import com.financetracker.notifications.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The household meal call (ADR-025): one member calls everyone to the table and the others are notified and can
 * answer. Uses the household to know who to call (ADR-022) and the notification subsystem to reach them
 * (ADR-021). Writes nothing to any finance surface.
 */
@Service
@RequiredArgsConstructor
public class MealService {

    private static final int RECENT = 20;
    private static final String DEFAULT_MESSAGE = "Come to the table";

    private final MealCallRepository calls;
    private final MealCallAckRepository acks;
    private final HouseholdService households;
    private final NotificationService notifications;

    /** Start a meal call and notify every other household member. */
    @Transactional
    public MealCall call(UUID userId, String message) {
        UUID householdId = households.householdIdOf(userId)
            .orElseThrow(() -> new ConflictException("Join or create a household before calling a meal"));
        String text = message == null || message.isBlank() ? DEFAULT_MESSAGE : message.strip();
        MealCall call = calls.save(MealCall.builder()
            .householdId(householdId).callerUserId(userId).message(text).build());
        for (UUID member : households.memberUserIds(householdId)) {
            if (!member.equals(userId)) {
                notifications.notify(member, "meal.call", text, "Tap to respond", "/meals/calls/" + call.getId());
            }
        }
        return call;
    }

    /** Recent meal calls for the caller's household, newest first. */
    @Transactional(readOnly = true)
    public List<MealCall> recent(UUID userId) {
        UUID householdId = requireHousehold(userId);
        return calls.findByHouseholdIdOrderByCreatedAtDesc(householdId, PageRequest.of(0, RECENT));
    }

    /** One meal call in the caller's household. */
    @Transactional(readOnly = true)
    public MealCall requireCall(UUID userId, UUID callId) {
        UUID householdId = requireHousehold(userId);
        MealCall call = calls.findById(callId)
            .orElseThrow(() -> new EntityNotFoundException("Meal call not found"));
        if (!call.getHouseholdId().equals(householdId)) {
            throw new EntityNotFoundException("Meal call not found");
        }
        return call;
    }

    /** The answers to a meal call. */
    @Transactional(readOnly = true)
    public List<MealCallAck> acksOf(UUID callId) {
        return acks.findByMealCallId(callId);
    }

    /** Answer a meal call; a later answer replaces the member's earlier one. */
    @Transactional
    public void respond(UUID userId, UUID callId, MealResponse response) {
        requireCall(userId, callId);
        MealCallAck ack = acks.findByMealCallIdAndUserId(callId, userId)
            .orElseGet(() -> MealCallAck.builder().mealCallId(callId).userId(userId).build());
        ack.setResponse(response);
        ack.setRespondedAt(Instant.now());
        acks.save(ack);
    }

    private UUID requireHousehold(UUID userId) {
        return households.householdIdOf(userId)
            .orElseThrow(() -> new ConflictException("Join or create a household first"));
    }
}
