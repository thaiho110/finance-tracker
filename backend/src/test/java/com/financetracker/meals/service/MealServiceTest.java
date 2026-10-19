package com.financetracker.meals.service;

import com.financetracker.common.exception.ConflictException;
import com.financetracker.household.service.HouseholdService;
import com.financetracker.meals.model.MealCall;
import com.financetracker.meals.model.MealCallAck;
import com.financetracker.meals.model.MealResponse;
import com.financetracker.meals.repository.MealCallAckRepository;
import com.financetracker.meals.repository.MealCallRepository;
import com.financetracker.notifications.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MealService Unit Tests")
class MealServiceTest {

    @Mock private MealCallRepository calls;
    @Mock private MealCallAckRepository acks;
    @Mock private HouseholdService households;
    @Mock private NotificationService notifications;

    private MealService mealService;

    private final UUID caller = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final UUID householdId = UUID.randomUUID();
    private final UUID callId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mealService = new MealService(calls, acks, households, notifications);
    }

    @Test
    @DisplayName("Calling a meal without a household is rejected")
    void callRequiresHousehold() {
        when(households.householdIdOf(caller)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mealService.call(caller, null)).isInstanceOf(ConflictException.class);
        verify(calls, never()).save(any());
    }

    @Test
    @DisplayName("A meal call notifies every other member, not the caller")
    void notifiesOtherMembers() {
        when(households.householdIdOf(caller)).thenReturn(Optional.of(householdId));
        when(calls.save(any())).thenAnswer(invocation -> {
            MealCall call = invocation.getArgument(0);
            call.setId(callId);
            return call;
        });
        when(households.memberUserIds(householdId)).thenReturn(List.of(caller, other));

        mealService.call(caller, "Dinner");

        verify(notifications).notify(eq(other), eq("meal.call"), eq("Dinner"), any(), contains(callId.toString()));
        verify(notifications, never()).notify(eq(caller), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Responding records the member's answer")
    void respondSavesAck() {
        when(households.householdIdOf(other)).thenReturn(Optional.of(householdId));
        when(calls.findById(callId)).thenReturn(Optional.of(
            MealCall.builder().id(callId).householdId(householdId).callerUserId(caller).build()));
        when(acks.findByMealCallIdAndUserId(callId, other)).thenReturn(Optional.empty());

        mealService.respond(other, callId, MealResponse.COMING);

        verify(acks).save(any(MealCallAck.class));
    }
}
