package com.hashwhale.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "USD-denominated summary of active simulated Earn positions")
public record EarnSummaryResponse(
        @Schema(example = "1500.00") BigDecimal totalPrincipalUsd,
        @Schema(example = "8.42") BigDecimal accruedRewardsUsd,
        @Schema(example = "4.75") BigDecimal weightedAverageApy,
        @Schema(example = "2") long activePositions,
        @Schema(nullable = true, example = "2026-11-29") LocalDate nextMaturityDate) {}
