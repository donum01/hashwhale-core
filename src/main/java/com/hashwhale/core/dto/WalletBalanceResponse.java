package com.hashwhale.core.dto;

import com.hashwhale.core.entity.Asset;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Balance for one wallet asset")
public record WalletBalanceResponse(
        @Schema(example = "BTC") Asset asset,
        @Schema(example = "0.75000000") BigDecimal availableAmount,
        @Schema(example = "0.25000000") BigDecimal lockedAmount) {
}
