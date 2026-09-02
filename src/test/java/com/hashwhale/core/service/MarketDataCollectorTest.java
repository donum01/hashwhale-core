package com.hashwhale.core.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

class MarketDataCollectorTest {

    private static final Instant NOW = Instant.parse("2026-08-31T12:00:00Z");

    @Test
    void coldStartSeedsOnlyAssetsWithoutRowsAndEnsuresFiatRatesExist() throws Exception {
        CoinGeckoMarketDataClient client = mock(CoinGeckoMarketDataClient.class);
        MarketDataPersistenceService persistence = mock(MarketDataPersistenceService.class);
        MarketPricePointRepository priceRepository = mock(MarketPricePointRepository.class);
        ExchangeRatePointRepository rateRepository = mock(ExchangeRatePointRepository.class);
        when(priceRepository.countByAsset(Asset.BTC)).thenReturn(0L);
        when(priceRepository.countByAsset(Asset.ETH)).thenReturn(5L);
        when(priceRepository.countByAsset(Asset.USDT)).thenReturn(0L);
        MarketPriceHistory btcHistory = history(Asset.BTC, "60000", "61000");
        MarketPriceHistory usdtHistory = history(Asset.USDT, "0.999", "1.001");
        when(client.fetchPriceHistory(
                        Asset.BTC, FiatCurrency.USD, PriceHistoryRange.NINETY_DAYS))
                .thenReturn(btcHistory);
        when(client.fetchPriceHistory(
                        Asset.USDT, FiatCurrency.USD, PriceHistoryRange.NINETY_DAYS))
                .thenReturn(usdtHistory);
        CoinGeckoExchangeRateSnapshot rates = new CoinGeckoExchangeRateSnapshot(
                Map.of(FiatCurrency.USD, BigDecimal.ONE), NOW);
        when(client.fetchFiatPerUsdRates()).thenReturn(rates);
        stubLatestSnapshots(priceRepository, rateRepository, NOW);
        MarketDataCollector collector = collector(
                client, persistence, priceRepository, rateRepository, configuration(0, 1));

        collector.run(mock(ApplicationArguments.class));

        verify(persistence).seedUsdHistory(Asset.BTC, btcHistory.points());
        verify(persistence).seedUsdHistory(Asset.USDT, usdtHistory.points());
        verify(client, never()).fetchPriceHistory(
                Asset.ETH, FiatCurrency.USD, PriceHistoryRange.NINETY_DAYS);
        verify(persistence).appendExchangeRates(rates);
    }

    @Test
    void startupImmediatelyRefreshesExistingStaleSnapshots() {
        CoinGeckoMarketDataClient client = mock(CoinGeckoMarketDataClient.class);
        MarketDataPersistenceService persistence = mock(MarketDataPersistenceService.class);
        MarketPricePointRepository priceRepository = mock(MarketPricePointRepository.class);
        ExchangeRatePointRepository rateRepository = mock(ExchangeRatePointRepository.class);
        stubExistingRows(priceRepository, rateRepository);
        stubLatestSnapshots(priceRepository, rateRepository, NOW.minusSeconds(3600));

        CoinGeckoPriceSnapshot prices = pricesAt(NOW);
        CoinGeckoExchangeRateSnapshot rates = new CoinGeckoExchangeRateSnapshot(
                Map.of(FiatCurrency.USD, BigDecimal.ONE), NOW);
        when(client.fetchUsdPrices()).thenReturn(prices);
        when(client.fetchFiatPerUsdRates()).thenReturn(rates);
        MarketDataCollector collector = collector(
                client, persistence, priceRepository, rateRepository, configuration(0, 1));

        collector.run(mock(ApplicationArguments.class));

        verify(client).fetchUsdPrices();
        verify(persistence).appendCurrentPrices(prices);
        verify(persistence).appendExchangeRates(rates);
        verify(client, never()).fetchPriceHistory(any(), any(), any());
    }

    @Test
    void startupDoesNotCallProviderWhenStoredSnapshotsAreFresh() {
        CoinGeckoMarketDataClient client = mock(CoinGeckoMarketDataClient.class);
        MarketDataPersistenceService persistence = mock(MarketDataPersistenceService.class);
        MarketPricePointRepository priceRepository = mock(MarketPricePointRepository.class);
        ExchangeRatePointRepository rateRepository = mock(ExchangeRatePointRepository.class);
        stubExistingRows(priceRepository, rateRepository);
        stubLatestSnapshots(priceRepository, rateRepository, NOW);
        MarketDataCollector collector = collector(
                client, persistence, priceRepository, rateRepository, configuration(0, 1));

        collector.run(mock(ApplicationArguments.class));

        verify(client, never()).fetchUsdPrices();
        verify(client, never()).fetchFiatPerUsdRates();
        verify(client, never()).fetchPriceHistory(any(), any(), any());
    }

    @Test
    void scheduledCollectionBacksOffAndRetriesOnlyAfterA429() {
        CoinGeckoMarketDataClient client = mock(CoinGeckoMarketDataClient.class);
        MarketDataPersistenceService persistence = mock(MarketDataPersistenceService.class);
        CoinGeckoPriceSnapshot prices = new CoinGeckoPriceSnapshot(Map.of(
                Asset.BTC, new MarketPriceSample(NOW, new BigDecimal("60000")),
                Asset.ETH, new MarketPriceSample(NOW, new BigDecimal("3000")),
                Asset.USDT, new MarketPriceSample(NOW, BigDecimal.ONE)));
        CoinGeckoExchangeRateSnapshot rates = new CoinGeckoExchangeRateSnapshot(
                Map.of(FiatCurrency.USD, BigDecimal.ONE), NOW);
        when(client.fetchUsdPrices())
                .thenThrow(new CoinGeckoRateLimitException(1))
                .thenReturn(prices);
        when(client.fetchFiatPerUsdRates()).thenReturn(rates);
        MarketDataCollector collector = collector(
                client,
                persistence,
                mock(MarketPricePointRepository.class),
                mock(ExchangeRatePointRepository.class),
                configuration(1, 1));

        collector.collectLatestPoints();

        verify(client, times(2)).fetchUsdPrices();
        verify(persistence).appendCurrentPrices(prices);
        verify(persistence).appendExchangeRates(rates);
    }

    @Test
    void coldStartFailureDoesNotPreventApplicationStartup() {
        CoinGeckoMarketDataClient client = mock(CoinGeckoMarketDataClient.class);
        MarketDataPersistenceService persistence = mock(MarketDataPersistenceService.class);
        MarketPricePointRepository priceRepository = mock(MarketPricePointRepository.class);
        ExchangeRatePointRepository rateRepository = mock(ExchangeRatePointRepository.class);
        when(priceRepository.countByAsset(Asset.BTC)).thenReturn(0L);
        when(client.fetchPriceHistory(
                        Asset.BTC, FiatCurrency.USD, PriceHistoryRange.NINETY_DAYS))
                .thenThrow(new IllegalStateException("CoinGecko is unreachable"));
        MarketDataCollector collector = collector(
                client, persistence, priceRepository, rateRepository, configuration(0, 1));

        assertDoesNotThrow(() -> collector.run(mock(ApplicationArguments.class)));

        verify(persistence, never()).seedUsdHistory(any(), any());
    }

    private MarketDataCollector collector(
            CoinGeckoMarketDataClient client,
            MarketDataPersistenceService persistence,
            MarketPricePointRepository priceRepository,
            ExchangeRatePointRepository rateRepository,
            PricingConfigurationProperties configuration) {
        return new MarketDataCollector(
                client,
                persistence,
                priceRepository,
                rateRepository,
                configuration,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private PricingConfigurationProperties configuration(int retries, long backoffMs) {
        PricingConfigurationProperties configuration = new PricingConfigurationProperties();
        configuration.setRateLimitMaxRetries(retries);
        configuration.setRateLimitInitialBackoffMs(backoffMs);
        configuration.setRefreshMs(300000L);
        return configuration;
    }

    private void stubExistingRows(
            MarketPricePointRepository priceRepository,
            ExchangeRatePointRepository rateRepository) {
        for (Asset asset : Asset.values()) {
            when(priceRepository.countByAsset(asset)).thenReturn(1L);
        }
        for (FiatCurrency currency : FiatCurrency.values()) {
            when(rateRepository.countByQuoteCurrency(currency)).thenReturn(1L);
        }
    }

    private void stubLatestSnapshots(
            MarketPricePointRepository priceRepository,
            ExchangeRatePointRepository rateRepository,
            Instant timestamp) {
        for (Asset asset : Asset.values()) {
            MarketPricePoint point = new MarketPricePoint();
            point.setTimestamp(timestamp);
            when(priceRepository.findFirstByAssetAndQuoteCurrencyOrderByTimestampDesc(
                            asset, FiatCurrency.USD))
                    .thenReturn(Optional.of(point));
        }
        for (FiatCurrency currency : FiatCurrency.values()) {
            ExchangeRatePoint point = new ExchangeRatePoint();
            point.setTimestamp(timestamp);
            when(rateRepository.findFirstByQuoteCurrencyOrderByTimestampDesc(currency))
                    .thenReturn(Optional.of(point));
        }
    }

    private CoinGeckoPriceSnapshot pricesAt(Instant timestamp) {
        return new CoinGeckoPriceSnapshot(Map.of(
                Asset.BTC, new MarketPriceSample(timestamp, new BigDecimal("60000")),
                Asset.ETH, new MarketPriceSample(timestamp, new BigDecimal("3000")),
                Asset.USDT, new MarketPriceSample(timestamp, BigDecimal.ONE)));
    }

    private MarketPriceHistory history(Asset asset, String first, String second) {
        return new MarketPriceHistory(
                asset,
                FiatCurrency.USD,
                PriceHistoryRange.NINETY_DAYS,
                List.of(
                        new MarketPriceSample(NOW.minusSeconds(3600), new BigDecimal(first)),
                        new MarketPriceSample(NOW, new BigDecimal(second))),
                PriceSource.COINGECKO,
                NOW,
                false);
    }
}
