package com.hashwhale.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "One timestamped market-price observation")
public record MarketPricePointResponse(
        @Schema(example = "2026-08-31T12:00:00Z") Instant timestamp,
        @Schema(example = "60250.42") BigDecimal price) {}
