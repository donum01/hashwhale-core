package com.hashwhale.core.service;

import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.FiatCurrency;
import com.hashwhale.core.entity.PriceSource;
import java.time.Instant;
import java.util.List;

public record MarketPriceHistory(
        Asset asset,
        FiatCurrency quoteCurrency,
        PriceHistoryRange range,
        List<MarketPriceSample> points,
        PriceSource source,
        Instant updatedAt,
        boolean stale) {}
