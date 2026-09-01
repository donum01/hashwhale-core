package com.hashwhale.core.dto;

import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.EarnTermType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "A server-controlled simulated Earn product")
public record EarnProductResponse(
        @Schema(example = "USDT_LOCKED_90") String id,
        @Schema(example = "USDT") Asset asset,
        @Schema(example = "LOCKED_90") EarnTermType termType,
        @Schema(description = "Annual percentage yield", example = "6.00") BigDecimal apy,
        @Schema(example = "90") int termDays,
        @Schema(example = "10") BigDecimal minimumAmount,
        boolean flexible,
        boolean active) {}
