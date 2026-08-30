package com.hashwhale.core.service;

import com.hashwhale.core.entity.Asset;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Temporary deterministic price source used until a live market-data provider is integrated.
 */
@Service
public class StaticPriceService implements PriceService {

    private static final Map<Asset, BigDecimal> USD_PRICES = Map.of(
            Asset.BTC, new BigDecimal("60000"),
            Asset.ETH, new BigDecimal("3000"),
            Asset.USDT, BigDecimal.ONE,
            Asset.USDC, BigDecimal.ONE);

    @Override
    public BigDecimal getUsdPrice(Asset asset) {
        BigDecimal price = USD_PRICES.get(asset);
        if (price == null) {
            throw new IllegalArgumentException("No USD price configured for asset: " + asset);
        }
        return price;
    }
}
