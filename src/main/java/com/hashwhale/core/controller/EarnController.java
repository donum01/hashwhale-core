package com.hashwhale.core.controller;

import com.hashwhale.core.dto.ApiErrorResponse;
import com.hashwhale.core.dto.EarnPositionResponse;
import com.hashwhale.core.dto.EarnProductResponse;
import com.hashwhale.core.dto.EarnSubscribeRequest;
import com.hashwhale.core.dto.EarnSummaryResponse;
import com.hashwhale.core.entity.EarnPosition;
import com.hashwhale.core.entity.EarnTermType;
import com.hashwhale.core.entity.User;
import com.hashwhale.core.service.EarnProduct;
import com.hashwhale.core.service.EarnService;
import com.hashwhale.core.service.EarnSummary;
import com.hashwhale.core.service.ForbiddenException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/earn")
@Tag(name = "Earn", description = "Manage simulated flexible and locked Earn positions")
public class EarnController {

    private final EarnService earnService;

    @GetMapping("/products")
    @Operation(summary = "List available simulated Earn products")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Products returned",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = EarnProductResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired JWT")
    })
    public ResponseEntity<List<EarnProductResponse>> getProducts() {
        getAuthenticatedUser();
        return ResponseEntity.ok(earnService.getProducts().stream().map(this::toProductResponse).toList());
    }

    @GetMapping("/summary")
    @Operation(summary = "Get the authenticated user's simulated Earn summary")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Summary returned",
                    content = @Content(schema = @Schema(implementation = EarnSummaryResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired JWT")
    })
    public ResponseEntity<EarnSummaryResponse> getSummary() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(toSummaryResponse(earnService.getSummary(user.getId())));
    }

    @GetMapping("/positions")
    @Operation(summary = "List the authenticated user's simulated Earn positions")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Positions returned",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = EarnPositionResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired JWT")
    })
    public ResponseEntity<List<EarnPositionResponse>> getPositions() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(earnService.getPositions(user.getId()).stream()
                .map(this::toPositionResponse)
                .toList());
    }

    @PostMapping("/positions")
    @Operation(
            summary = "Subscribe to a simulated Earn product",
            description = "Moves funds from available to locked balance in the demo ledger; no real assets move.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Earn position created",
                    content = @Content(schema = @Schema(implementation = EarnPositionResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid product, amount, or insufficient available balance",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired JWT"),
            @ApiResponse(responseCode = "409", description = "Product is unavailable")
    })
    public ResponseEntity<EarnPositionResponse> subscribe(
            @Valid @RequestBody EarnSubscribeRequest request) {
        User user = getAuthenticatedUser();
        EarnPosition position = earnService.subscribe(user.getId(), request.getProductId(), request.getAmount());
        return ResponseEntity.created(URI.create("/api/earn/positions/" + position.getId()))
                .body(toPositionResponse(position));
    }

    @PostMapping("/positions/{positionId}/withdraw")
    @Operation(
            summary = "Withdraw a simulated Earn position",
            description = "Flexible positions can be withdrawn immediately; locked positions must reach maturity.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Principal and accrued simulated rewards returned",
                    content = @Content(schema = @Schema(implementation = EarnPositionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Position does not exist"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired JWT"),
            @ApiResponse(responseCode = "403", description = "Authenticated user does not own the position"),
            @ApiResponse(responseCode = "409", description = "Position is not active or has not matured")
    })
    public ResponseEntity<EarnPositionResponse> withdraw(
            @PathVariable @Positive Long positionId) {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(toPositionResponse(earnService.withdraw(positionId, user.getId())));
    }

    private EarnProductResponse toProductResponse(EarnProduct product) {
        return new EarnProductResponse(
                product.id(),
                product.asset(),
                product.termType(),
                product.apy(),
                earnService.termDays(product.termType()),
                product.minimumAmount(),
                product.termType() == EarnTermType.FLEXIBLE,
                product.active());
    }

    private EarnPositionResponse toPositionResponse(EarnPosition position) {
        return new EarnPositionResponse(
                position.getId(),
                position.getAsset(),
                position.getPrincipalAmount(),
                position.getApy(),
                position.getTermType(),
                position.getStartDate(),
                position.getEndDate(),
                position.getStatus(),
                earnService.calculateAccruedRewards(position),
                earnService.calculateEstimatedRewardsAtMaturity(position),
                earnService.isWithdrawable(position),
                earnService.daysRemaining(position));
    }

    private EarnSummaryResponse toSummaryResponse(EarnSummary summary) {
        return new EarnSummaryResponse(
                summary.totalPrincipalUsd(),
                summary.accruedRewardsUsd(),
                summary.weightedAverageApy(),
                summary.activePositions(),
                summary.nextMaturityDate());
    }

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            throw new ForbiddenException("Authenticated user principal is unavailable");
        }
        return user;
    }
}
