package com.hashwhale.core.service;

import com.hashwhale.core.config.PricingConfigurationProperties;
import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.ExchangeRatePoint;
import com.hashwhale.core.entity.FiatCurrency;
import com.hashwhale.core.entity.MarketPricePoint;
import com.hashwhale.core.entity.PriceSource;
import com.hashwhale.core.repository.ExchangeRatePointRepository;
import com.hashwhale.core.repository.MarketPricePointRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarketHistoryService {

    private final MarketPricePointRepository marketPricePointRepository;
    private final ExchangeRatePointRepository exchangeRatePointRepository;
    private final PricingConfigurationProperties configuration;
    private final Clock applicationClock;

    @Transactional(readOnly = true)
    public MarketPriceHistory getHistory(
            Asset asset, FiatCurrency quoteCurrency, PriceHistoryRange range) {
        if (asset == null || quoteCurrency == null || range == null) {
            throw new IllegalArgumentException("Asset, quote currency, and range are required");
        }

        Instant now = applicationClock.instant();
        Instant from = now.minus(range.duration());
        List<MarketPricePoint> storedPoints = marketPricePointRepository
                .findByAssetAndQuoteCurrencyAndTimestampBetweenOrderByTimestampAsc(
                        asset, FiatCurrency.USD, from, now);
        if (storedPoints.size() < 2) {
            throw new MarketDataUnavailableException(
                    "Stored market history is not yet available for " + asset + " " + range.apiValue());
        }

        List<MarketPriceSample> samples = quoteCurrency == FiatCurrency.USD
                ? storedPoints.stream()
                        .map(point -> new MarketPriceSample(point.getTimestamp(), point.getPrice()))
                        .toList()
                : convertUsingClosestRates(storedPoints, quoteCurrency);

        MarketPricePoint newestPoint = storedPoints.get(storedPoints.size() - 1);
        boolean stale = newestPoint.getTimestamp()
                .plusMillis(configuration.getRefreshMs() * 2)
                .isBefore(now);
        if (quoteCurrency != FiatCurrency.USD) {
            Instant latestRateTimestamp = exchangeRatePointRepository
                    .findFirstByQuoteCurrencyOrderByTimestampDesc(quoteCurrency)
                    .map(ExchangeRatePoint::getTimestamp)
                    .orElse(Instant.EPOCH);
            stale = stale || latestRateTimestamp
                    .plusMillis(configuration.getRefreshMs() * 2)
                    .isBefore(now);
        }

        PriceSource source = newestPoint.getSource();
        return new MarketPriceHistory(
                asset,
                quoteCurrency,
                range,
                List.copyOf(samples),
                source,
                newestPoint.getTimestamp(),
                stale);
    }

    private List<MarketPriceSample> convertUsingClosestRates(
            List<MarketPricePoint> prices, FiatCurrency quoteCurrency) {
        Instant firstTimestamp = prices.get(0).getTimestamp();
        Instant lastTimestamp = prices.get(prices.size() - 1).getTimestamp();
        Map<Instant, ExchangeRatePoint> ratesByTimestamp = new LinkedHashMap<>();

        exchangeRatePointRepository
                .findFirstByQuoteCurrencyAndTimestampLessThanEqualOrderByTimestampDesc(
                        quoteCurrency, firstTimestamp)
                .ifPresent(rate -> ratesByTimestamp.put(rate.getTimestamp(), rate));
        exchangeRatePointRepository
                .findByQuoteCurrencyAndTimestampBetweenOrderByTimestampAsc(
                        quoteCurrency, firstTimestamp, lastTimestamp)
                .forEach(rate -> ratesByTimestamp.put(rate.getTimestamp(), rate));
        exchangeRatePointRepository
                .findFirstByQuoteCurrencyAndTimestampGreaterThanOrderByTimestampAsc(
                        quoteCurrency, lastTimestamp)
                .ifPresent(rate -> ratesByTimestamp.put(rate.getTimestamp(), rate));

        List<ExchangeRatePoint> rates = new ArrayList<>(ratesByTimestamp.values());
        rates.sort(Comparator.comparing(ExchangeRatePoint::getTimestamp));
        if (rates.isEmpty()) {
            throw new MarketDataUnavailableException(
                    "Stored " + quoteCurrency + " exchange rates are not yet available");
        }

        return prices.stream()
                .map(point -> {
                    ExchangeRatePoint closestRate = closestRate(rates, point.getTimestamp());
                    BigDecimal convertedPrice = point.getPrice()
                            .multiply(closestRate.getFiatPerUsd());
                    return new MarketPriceSample(point.getTimestamp(), convertedPrice);
                })
                .toList();
    }

    private ExchangeRatePoint closestRate(List<ExchangeRatePoint> rates, Instant timestamp) {
        int low = 0;
        int high = rates.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            Instant candidate = rates.get(middle).getTimestamp();
            int comparison = candidate.compareTo(timestamp);
            if (comparison == 0) {
                return rates.get(middle);
            }
            if (comparison < 0) {
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        if (high < 0) {
            return rates.get(0);
        }
        if (low >= rates.size()) {
            return rates.get(rates.size() - 1);
        }

        ExchangeRatePoint before = rates.get(high);
        ExchangeRatePoint after = rates.get(low);
        Duration distanceBefore = Duration.between(before.getTimestamp(), timestamp).abs();
        Duration distanceAfter = Duration.between(timestamp, after.getTimestamp()).abs();
        return distanceBefore.compareTo(distanceAfter) <= 0 ? before : after;
    }
}
