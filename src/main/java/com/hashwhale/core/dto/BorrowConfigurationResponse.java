package com.hashwhale.core.dto;

import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.PriceSource;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Schema(description = "Effective prices and risk parameters used by the borrow engine")
public record BorrowConfigurationResponse(
        @Schema(
                        description = "Current USD price keyed by asset symbol",
                        example = "{\"BTC\":60000,\"ETH\":3000,\"USDT\":1}")
                Map<Asset, BigDecimal> usdPrices,
        @Schema(description = "Provider currently supplying the returned prices", example = "COINGECKO")
                PriceSource priceSource,
        @Schema(
                        description = "Oldest provider update timestamp for the returned live prices",
                        example = "2026-08-31T12:00:00Z",
                        nullable = true)
                Instant pricesUpdatedAt,
        @Schema(description = "Whether live prices are unavailable or older than the configured threshold")
                boolean pricesStale,
        @Schema(description = "APR applied to newly created loans", example = "2.88")
                BigDecimal interestRateApr,
        @Schema(description = "Maximum permitted origination LTV percentage", example = "70")
                BigDecimal maxLtvPercent,
        @Schema(description = "LTV percentage at which the UI begins warning the user", example = "50")
                BigDecimal warningLtvPercent,
        @Schema(description = "LTV percentage used for liquidation-price estimates", example = "85")
                BigDecimal liquidationLtvPercent) {}
