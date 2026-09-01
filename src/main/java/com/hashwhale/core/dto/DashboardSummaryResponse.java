package com.hashwhale.core.dto;

import com.hashwhale.core.entity.FiatCurrency;
import com.hashwhale.core.entity.KycStatus;
import com.hashwhale.core.entity.PriceSource;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Authenticated user's cross-product dashboard summary")
public record DashboardSummaryResponse(
        String email,
        KycStatus kycStatus,
        FiatCurrency preferredFiatCurrency,
        BigDecimal netAccountValueUsd,
        BigDecimal totalAssetsUsd,
        @Schema(description = "Outstanding active-loan principal in USD") BigDecimal totalDebtUsd,
        BigDecimal availableUsd,
        BigDecimal earnPrincipalUsd,
        BigDecimal collateralUsd,
        BigDecimal accruedEarnRewardsUsd,
        long activeLoanCount,
        BigDecimal highestLtvPercent,
        BorrowHealth borrowHealth,
        long activeEarnPositionCount,
        BigDecimal weightedAverageEarnApy,
        LocalDate nextEarnMaturityDate,
        PriceSource priceSource,
        Instant pricesUpdatedAt,
        boolean pricesStale,
        List<DashboardAlertResponse> alerts,
        DashboardRecommendationResponse recommendation,
        List<TransactionResponse> recentTransactions) {}
