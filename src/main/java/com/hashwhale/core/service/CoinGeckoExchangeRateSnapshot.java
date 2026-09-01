package com.hashwhale.core.service;

import com.hashwhale.core.entity.FiatCurrency;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

record CoinGeckoExchangeRateSnapshot(
        Map<FiatCurrency, BigDecimal> fiatPerUsd,
        Instant timestamp) {

    CoinGeckoExchangeRateSnapshot {
        fiatPerUsd = Map.copyOf(fiatPerUsd);
    }
}
