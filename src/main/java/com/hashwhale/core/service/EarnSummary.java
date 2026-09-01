package com.hashwhale.core.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EarnSummary(
        BigDecimal totalPrincipalUsd,
        BigDecimal accruedRewardsUsd,
        BigDecimal weightedAverageApy,
        long activePositions,
        LocalDate nextMaturityDate) {}
