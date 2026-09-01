package com.hashwhale.core.service;

import com.hashwhale.core.config.PricingConfigurationProperties;
import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.PriceSource;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Configuration-backed deterministic price source used until a live market-data provider is integrated.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.pricing.provider", havingValue = "static")
public class StaticPriceService implements PriceService {

    private final PricingConfigurationProperties configuration;

    @Override
    public BigDecimal getUsdPrice(Asset asset) {
        BigDecimal price = configuration.getFallbackUsdPrices().get(asset);
        if (price == null) {
            throw new IllegalArgumentException("No USD price configured for asset: " + asset);
        }
        return price;
    }

    @Override
    public PriceServiceStatus getStatus() {
        return new PriceServiceStatus(PriceSource.STATIC, null, false);
    }
}
