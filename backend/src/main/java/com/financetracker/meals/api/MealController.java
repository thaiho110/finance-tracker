package com.financetracker.meals.api;

import com.financetracker.auth.security.AuthenticatedUser;
import com.financetracker.auth.security.CurrentUser;
import com.financetracker.meals.dto.AckView;
import com.financetracker.meals.dto.CallMealRequest;
import com.financetracker.meals.dto.MealCallResponse;
import com.financetracker.meals.dto.RespondRequest;
import com.financetracker.meals.model.MealCall;
import com.financetracker.meals.service.MealService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The household meal call (ADR-025 §6). In the MEALS module; requires a household. Calling needs
 * {@code MEALS_CALL}, responding {@code MEALS_RESPOND}; reading needs either.
 */
@RestController
@RequestMapping("/api/v1/meals")
@RequiredArgsConstructor
@Tag(name = "Meals", description = "Household meal calls and responses")
public class MealController {

    private final MealService mealService;

    @PostMapping("/calls")
    @Operation(summary = "Call the household to a meal; notifies every other member")
    public ResponseEntity<MealCallResponse> call(@Valid @RequestBody CallMealRequest request,
                                                 @CurrentUser AuthenticatedUser user) {
        MealCall call = mealService.call(user.getId(), request.message());
        return ResponseEntity.ok(toResponse(call, List.of()));
    }

    @GetMapping("/calls")
    @Operation(summary = "Recent meal calls for the caller's household, with responses")
    public ResponseEntity<List<MealCallResponse>> recent(@CurrentUser AuthenticatedUser user) {
        List<MealCallResponse> rows = mealService.recent(user.getId()).stream()
            .map(call -> toResponse(call, mealService.acksOf(call.getId())))
            .toList();
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/calls/{id}")
    @Operation(summary = "One meal call and who is coming")
    public ResponseEntity<MealCallResponse> one(@PathVariable UUID id, @CurrentUser AuthenticatedUser user) {
        MealCall call = mealService.requireCall(user.getId(), id);
        return ResponseEntity.ok(toResponse(call, mealService.acksOf(id)));
    }

    @PostMapping("/calls/{id}/respond")
    @Operation(summary = "Answer a meal call: COMING, LATER or SKIP")
    public ResponseEntity<MealCallResponse> respond(@PathVariable UUID id,
                                                    @Valid @RequestBody RespondRequest request,
                                                    @CurrentUser AuthenticatedUser user) {
        mealService.respond(user.getId(), id, request.response());
        MealCall call = mealService.requireCall(user.getId(), id);
        return ResponseEntity.ok(toResponse(call, mealService.acksOf(id)));
    }

    private MealCallResponse toResponse(MealCall call, List<com.financetracker.meals.model.MealCallAck> acks) {
        List<AckView> responses = acks.stream()
            .map(ack -> new AckView(ack.getUserId(), ack.getResponse(), ack.getRespondedAt()))
            .toList();
        return new MealCallResponse(call.getId(), call.getCallerUserId(), call.getMessage(), call.getCreatedAt(), responses);
    }
}
