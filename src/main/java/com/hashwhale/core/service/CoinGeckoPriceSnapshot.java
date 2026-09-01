package com.hashwhale.core.service;

import com.hashwhale.core.entity.Asset;
import java.util.Map;

record CoinGeckoPriceSnapshot(Map<Asset, MarketPriceSample> points) {

    CoinGeckoPriceSnapshot {
        points = Map.copyOf(points);
    }
}
