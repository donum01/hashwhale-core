package com.hashwhale.core.service;

import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.ExchangeRatePoint;
import com.hashwhale.core.entity.FiatCurrency;
import com.hashwhale.core.entity.MarketPricePoint;
import com.hashwhale.core.entity.PriceSource;
import com.hashwhale.core.repository.ExchangeRatePointRepository;
import com.hashwhale.core.repository.MarketPricePointRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarketDataPersistenceService {

    private final MarketPricePointRepository marketPricePointRepository;
    private final ExchangeRatePointRepository exchangeRatePointRepository;

    @Transactional
    public void appendCurrentPrices(CoinGeckoPriceSnapshot snapshot) {
        List<MarketPricePoint> newPoints = new ArrayList<>();
        snapshot.points().forEach((asset, point) -> {
            if (!marketPricePointRepository.existsByAssetAndQuoteCurrencyAndTimestamp(
                    asset, FiatCurrency.USD, point.timestamp())) {
                newPoints.add(marketPoint(asset, point));
            }
        });
        marketPricePointRepository.saveAll(newPoints);
    }

    @Transactional
    public void seedUsdHistory(Asset asset, List<MarketPriceSample> samples) {
        Map<java.time.Instant, MarketPriceSample> uniqueSamples = new TreeMap<>();
        samples.forEach(sample -> uniqueSamples.put(sample.timestamp(), sample));
        List<MarketPricePoint> entities = uniqueSamples.values().stream()
                .map(sample -> marketPoint(asset, sample))
                .toList();
        marketPricePointRepository.saveAll(entities);
    }

    @Transactional
    public void appendExchangeRates(CoinGeckoExchangeRateSnapshot snapshot) {
        List<ExchangeRatePoint> newRates = new ArrayList<>();
        snapshot.fiatPerUsd().forEach((currency, rate) -> {
            if (!exchangeRatePointRepository.existsByQuoteCurrencyAndTimestamp(
                    currency, snapshot.timestamp())) {
                ExchangeRatePoint point = new ExchangeRatePoint();
                point.setQuoteCurrency(currency);
                point.setTimestamp(snapshot.timestamp());
                point.setFiatPerUsd(rate);
                point.setSource(PriceSource.COINGECKO);
                newRates.add(point);
            }
        });
        exchangeRatePointRepository.saveAll(newRates);
    }

    private MarketPricePoint marketPoint(Asset asset, MarketPriceSample sample) {
        MarketPricePoint point = new MarketPricePoint();
        point.setAsset(asset);
        point.setQuoteCurrency(FiatCurrency.USD);
        point.setTimestamp(sample.timestamp());
        point.setPrice(sample.price());
        point.setSource(PriceSource.COINGECKO);
        return point;
    }
}
