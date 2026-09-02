package com.hashwhale.core.controller;

import com.hashwhale.core.dto.ApiErrorResponse;
import com.hashwhale.core.dto.EarnPositionResponse;
import com.hashwhale.core.dto.EarnProductResponse;
import com.hashwhale.core.dto.EarnSubscribeRequest;
import com.hashwhale.core.dto.EarnSummaryResponse;
import com.hashwhale.core.entity.EarnPosition;
import com.hashwhale.core.entity.EarnPositionStatus;
import com.hashwhale.core.entity.EarnTermType;
import com.hashwhale.core.entity.User;
import com.hashwhale.core.service.EarnProduct;
import com.hashwhale.core.service.EarnService;
import com.hashwhale.core.service.EarnSummary;
import com.hashwhale.core.service.ForbiddenException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
                    headers = {
                            @Header(
                                    name = "X-Has-More",
                                    description = "Whether another batch is available",
                                    schema = @Schema(type = "boolean")),
                            @Header(
                                    name = "X-Next-Cursor",
                                    description = "Pass this value as beforeId to load the next batch",
                                    schema = @Schema(type = "integer", format = "int64"))
                    },
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = EarnPositionResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired JWT")
    })
    public ResponseEntity<List<EarnPositionResponse>> getPositions(
            @Parameter(description = "Maximum records to return", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit,
            @Parameter(description = "Cursor returned by the previous response", example = "125")
            @RequestParam(required = false) @Positive Long beforeId,
            @Parameter(description = "Optional repeatable position-status filter")
            @RequestParam(name = "status", required = false) Set<EarnPositionStatus> statuses) {
        User user = getAuthenticatedUser();
        Slice<EarnPosition> positionSlice = earnService.getPositions(
                user.getId(), statuses, beforeId, limit);
        List<EarnPositionResponse> positions = positionSlice.getContent().stream()
                .map(this::toPositionResponse)
                .toList();
        return historyResponse(positionSlice, positions);
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

    private ResponseEntity<List<EarnPositionResponse>> historyResponse(
            Slice<EarnPosition> slice,
            List<EarnPositionResponse> body) {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header("X-Has-More", Boolean.toString(slice.hasNext()));
        if (slice.hasNext() && !slice.getContent().isEmpty()) {
            EarnPosition last = slice.getContent().get(slice.getContent().size() - 1);
            response.header("X-Next-Cursor", last.getId().toString());
        }
        return response.body(body);
    }

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            throw new ForbiddenException("Authenticated user principal is unavailable");
        }
        return user;
    }
}
