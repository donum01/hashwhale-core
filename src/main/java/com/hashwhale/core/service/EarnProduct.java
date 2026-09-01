package com.hashwhale.core.service;

import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.EarnTermType;
import java.math.BigDecimal;

public record EarnProduct(
        String id,
        Asset asset,
        EarnTermType termType,
        BigDecimal apy,
        BigDecimal minimumAmount,
        boolean active) {}
