package com.hashwhale.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MarketHistoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-31T12:00:00Z");

    @Test
    void returnsFreshUsdHistoryOnlyFromTheLocalRepository() {
        MarketPricePointRepository priceRepository = mock(MarketPricePointRepository.class);
        ExchangeRatePointRepository rateRepository = mock(ExchangeRatePointRepository.class);
        List<MarketPricePoint> stored = List.of(
                marketPoint(Asset.BTC, NOW.minusSeconds(3600), "60000"),
                marketPoint(Asset.BTC, NOW.minusSeconds(60), "61000"));
        when(priceRepository
                        .findByAssetAndQuoteCurrencyAndTimestampBetweenOrderByTimestampAsc(
                                Asset.BTC,
                                FiatCurrency.USD,
                                NOW.minusSeconds(86400),
                                NOW))
                .thenReturn(stored);
        MarketHistoryService service = service(priceRepository, rateRepository);

        MarketPriceHistory history = service.getHistory(
                Asset.BTC, FiatCurrency.USD, PriceHistoryRange.ONE_DAY);

        assertEquals(2, history.points().size());
        assertEquals(0, new BigDecimal("61000").compareTo(history.points().get(1).price()));
        assertEquals(PriceSource.COINGECKO, history.source());
        assertFalse(history.stale());
        verify(priceRepository)
                .findByAssetAndQuoteCurrencyAndTimestampBetweenOrderByTimestampAsc(
                        Asset.BTC,
                        FiatCurrency.USD,
                        NOW.minusSeconds(86400),
                        NOW);
    }

    @Test
    void convertsUsdtUsingTheClosestStoredFiatRateForEachTimestamp() {
        MarketPricePointRepository priceRepository = mock(MarketPricePointRepository.class);
        ExchangeRatePointRepository rateRepository = mock(ExchangeRatePointRepository.class);
        Instant firstPriceAt = NOW.minusSeconds(4 * 3600);
        Instant secondPriceAt = NOW.minusSeconds(60);
        List<MarketPricePoint> stored = List.of(
                marketPoint(Asset.USDT, firstPriceAt, "1.00"),
                marketPoint(Asset.USDT, secondPriceAt, "1.01"));
        when(priceRepository
                        .findByAssetAndQuoteCurrencyAndTimestampBetweenOrderByTimestampAsc(
                                Asset.USDT,
                                FiatCurrency.USD,
                                NOW.minusSeconds(86400),
                                NOW))
                .thenReturn(stored);

        ExchangeRatePoint before = exchangeRate(FiatCurrency.PHP, NOW.minusSeconds(5 * 3600), "56");
        ExchangeRatePoint middle = exchangeRate(FiatCurrency.PHP, NOW.minusSeconds(2 * 3600), "57");
        ExchangeRatePoint after = exchangeRate(FiatCurrency.PHP, NOW, "58");
        when(rateRepository
                        .findFirstByQuoteCurrencyAndTimestampLessThanEqualOrderByTimestampDesc(
                                FiatCurrency.PHP, firstPriceAt))
                .thenReturn(Optional.of(before));
        when(rateRepository.findByQuoteCurrencyAndTimestampBetweenOrderByTimestampAsc(
                        FiatCurrency.PHP, firstPriceAt, secondPriceAt))
                .thenReturn(List.of(middle));
        when(rateRepository
                        .findFirstByQuoteCurrencyAndTimestampGreaterThanOrderByTimestampAsc(
                                FiatCurrency.PHP, secondPriceAt))
                .thenReturn(Optional.of(after));
        when(rateRepository.findFirstByQuoteCurrencyOrderByTimestampDesc(FiatCurrency.PHP))
                .thenReturn(Optional.of(after));
        MarketHistoryService service = service(priceRepository, rateRepository);

        MarketPriceHistory history = service.getHistory(
                Asset.USDT, FiatCurrency.PHP, PriceHistoryRange.ONE_DAY);

        assertEquals(FiatCurrency.PHP, history.quoteCurrency());
        assertEquals(0, new BigDecimal("56.00").compareTo(history.points().get(0).price()));
        assertEquals(0, new BigDecimal("58.58").compareTo(history.points().get(1).price()));
        assertFalse(history.stale());
    }

    @Test
    void marksHistoryStaleAfterTwiceTheCollectorRefreshInterval() {
        MarketPricePointRepository priceRepository = mock(MarketPricePointRepository.class);
        ExchangeRatePointRepository rateRepository = mock(ExchangeRatePointRepository.class);
        when(priceRepository
                        .findByAssetAndQuoteCurrencyAndTimestampBetweenOrderByTimestampAsc(
                                Asset.ETH,
                                FiatCurrency.USD,
                                NOW.minusSeconds(86400),
                                NOW))
                .thenReturn(List.of(
                        marketPoint(Asset.ETH, NOW.minusSeconds(7200), "3000"),
                        marketPoint(Asset.ETH, NOW.minusSeconds(601), "3100")));

        MarketPriceHistory history = service(priceRepository, rateRepository).getHistory(
                Asset.ETH, FiatCurrency.USD, PriceHistoryRange.ONE_DAY);

        assertTrue(history.stale());
    }

    @Test
    void reportsUnavailableWhenTheDatabaseDoesNotContainAUsableSeries() {
        MarketPricePointRepository priceRepository = mock(MarketPricePointRepository.class);
        ExchangeRatePointRepository rateRepository = mock(ExchangeRatePointRepository.class);
        when(priceRepository
                        .findByAssetAndQuoteCurrencyAndTimestampBetweenOrderByTimestampAsc(
                                Asset.BTC,
                                FiatCurrency.USD,
                                NOW.minusSeconds(86400),
                                NOW))
                .thenReturn(List.of(marketPoint(Asset.BTC, NOW, "60000")));

        assertThrows(
                MarketDataUnavailableException.class,
                () -> service(priceRepository, rateRepository).getHistory(
                        Asset.BTC, FiatCurrency.USD, PriceHistoryRange.ONE_DAY));
    }

    private MarketHistoryService service(
            MarketPricePointRepository priceRepository,
            ExchangeRatePointRepository rateRepository) {
        PricingConfigurationProperties configuration = new PricingConfigurationProperties();
        configuration.setRefreshMs(300000);
        return new MarketHistoryService(
                priceRepository,
                rateRepository,
                configuration,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private MarketPricePoint marketPoint(Asset asset, Instant timestamp, String price) {
        MarketPricePoint point = new MarketPricePoint();
        point.setAsset(asset);
        point.setQuoteCurrency(FiatCurrency.USD);
        point.setTimestamp(timestamp);
        point.setPrice(new BigDecimal(price));
        point.setSource(PriceSource.COINGECKO);
        return point;
    }

    private ExchangeRatePoint exchangeRate(
            FiatCurrency currency, Instant timestamp, String fiatPerUsd) {
        ExchangeRatePoint point = new ExchangeRatePoint();
        point.setQuoteCurrency(currency);
        point.setTimestamp(timestamp);
        point.setFiatPerUsd(new BigDecimal(fiatPerUsd));
        point.setSource(PriceSource.COINGECKO);
        return point;
    }
}
