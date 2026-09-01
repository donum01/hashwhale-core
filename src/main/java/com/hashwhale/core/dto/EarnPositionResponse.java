package com.hashwhale.core.dto;

import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.EarnPositionStatus;
import com.hashwhale.core.entity.EarnTermType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "A user's simulated Earn position")
public record EarnPositionResponse(
        @Schema(example = "42") Long id,
        @Schema(example = "USDT") Asset asset,
        @Schema(example = "1000") BigDecimal principalAmount,
        @Schema(description = "APY snapshot captured at subscription", example = "6.00") BigDecimal apy,
        @Schema(example = "LOCKED_90") EarnTermType termType,
        @Schema(example = "2026-08-31") LocalDate startDate,
        @Schema(nullable = true, example = "2026-11-29") LocalDate endDate,
        @Schema(example = "ACTIVE") EarnPositionStatus status,
        @Schema(example = "4.109589041095890411") BigDecimal accruedRewards,
        @Schema(nullable = true, example = "14.794520547945205479") BigDecimal estimatedRewardsAtMaturity,
        boolean withdrawable,
        @Schema(nullable = true, example = "75") Long daysRemaining) {}
