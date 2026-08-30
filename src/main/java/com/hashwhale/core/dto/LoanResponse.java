package com.hashwhale.core.dto;

import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.LoanStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "A borrow position")
public record LoanResponse(
        @Schema(example = "42") Long id,
        @Schema(example = "BTC") Asset collateralAsset,
        @Schema(example = "0.50000000") BigDecimal collateralAmount,
        @Schema(example = "15000.00") BigDecimal borrowedAmount,
        @Schema(example = "USDT") Asset borrowedAsset,
        @Schema(description = "Annual percentage rate", example = "0.00") BigDecimal interestRateApr,
        @Schema(example = "ACTIVE") LoanStatus status,
        @Schema(example = "2026-08-30T12:00:00Z") Instant createdAt) {
}
