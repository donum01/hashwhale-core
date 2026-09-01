package com.hashwhale.core.dto;

import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.FiatCurrency;
import com.hashwhale.core.entity.PriceSource;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Historical market prices and derived period statistics")
public record MarketPriceHistoryResponse(
        @Schema(example = "BTC") Asset asset,
        @Schema(example = "USD") FiatCurrency quoteCurrency,
        @Schema(example = "7D", allowableValues = {"1D", "7D", "30D", "90D"}) String range,
        @Schema(example = "60250.42") BigDecimal currentPrice,
        @Schema(example = "1250.42") BigDecimal changeAmount,
        @Schema(example = "2.12") BigDecimal changePercent,
        @Schema(example = "58010.11") BigDecimal minimumPrice,
        @Schema(example = "61120.05") BigDecimal maximumPrice,
        PriceSource source,
        Instant updatedAt,
        boolean stale,
        List<MarketPricePointResponse> points) {}
