package com.hashwhale.core.service;

import com.hashwhale.core.config.PricingConfigurationProperties;
import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.FiatCurrency;
import com.hashwhale.core.entity.MarketPricePoint;
import com.hashwhale.core.entity.PriceSource;
import com.hashwhale.core.repository.MarketPricePointRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.pricing.provider", havingValue = "coingecko")
public class CoinGeckoPriceService implements PriceService {

    private final MarketPricePointRepository marketPricePointRepository;
    private final PricingConfigurationProperties configuration;
    private final Clock applicationClock;

    public CoinGeckoPriceService(
            MarketPricePointRepository marketPricePointRepository,
            PricingConfigurationProperties configuration,
            Clock applicationClock) {
        this.marketPricePointRepository = marketPricePointRepository;
        this.configuration = configuration;
        this.applicationClock = applicationClock;
    }

    @Override
    public BigDecimal getUsdPrice(Asset asset) {
        return marketPricePointRepository
                .findFirstByAssetAndQuoteCurrencyOrderByTimestampDesc(asset, FiatCurrency.USD)
                .map(MarketPricePoint::getPrice)
                .orElseGet(() -> fallbackPrice(asset));
    }

    @Override
    public PriceServiceStatus getStatus() {
        Map<Asset, MarketPricePoint> latestPoints = new EnumMap<>(Asset.class);
        for (Asset asset : Asset.values()) {
            marketPricePointRepository
                    .findFirstByAssetAndQuoteCurrencyOrderByTimestampDesc(
                            asset, FiatCurrency.USD)
                    .ifPresent(point -> latestPoints.put(asset, point));
        }
        if (latestPoints.size() != Asset.values().length) {
            return new PriceServiceStatus(PriceSource.STATIC_FALLBACK, null, true);
        }

        MarketPricePoint oldestLatestPoint = latestPoints.values().stream()
                .min(Comparator.comparing(MarketPricePoint::getTimestamp))
                .orElseThrow();
        Instant updatedAt = oldestLatestPoint.getTimestamp();
        boolean stale = updatedAt
                .plusMillis(configuration.getRefreshMs() * 2)
                .isBefore(applicationClock.instant());
        return new PriceServiceStatus(oldestLatestPoint.getSource(), updatedAt, stale);
    }

    private BigDecimal fallbackPrice(Asset asset) {
        BigDecimal price = configuration.getFallbackUsdPrices().get(asset);
        if (price == null) {
            throw new IllegalArgumentException("No USD price configured for asset: " + asset);
        }
        return price;
    }
}
