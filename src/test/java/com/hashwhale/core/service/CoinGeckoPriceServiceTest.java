package com.hashwhale.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hashwhale.core.config.PricingConfigurationProperties;
import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.FiatCurrency;
import com.hashwhale.core.entity.MarketPricePoint;
import com.hashwhale.core.entity.PriceSource;
import com.hashwhale.core.repository.MarketPricePointRepository;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CoinGeckoPriceServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-31T12:00:00Z");

    @Test
    void clientParsesCurrentBtcEthAndUsdtPointsWithProviderTimestamps() {
        CoinGeckoMarketDataClient client = client();

        CoinGeckoPriceSnapshot snapshot = client.parsePriceResponse("""
                {
                  "bitcoin": {"usd": 67425.12, "last_updated_at": 1788180000},
                  "ethereum": {"usd": 3421.55, "last_updated_at": 1788180005},
                  "tether": {"usd": 0.9998, "last_updated_at": 1788180008}
                }
                """);

        assertEquals(0, new BigDecimal("67425.12")
                .compareTo(snapshot.points().get(Asset.BTC).price()));
        assertEquals(0, new BigDecimal("3421.55")
                .compareTo(snapshot.points().get(Asset.ETH).price()));
        assertEquals(0, new BigDecimal("0.9998")
                .compareTo(snapshot.points().get(Asset.USDT).price()));
        assertEquals(
                Instant.ofEpochSecond(1788180008),
                snapshot.points().get(Asset.USDT).timestamp());
    }

    @Test
    void clientDerivesFiatPerUsdRatesFromCoinGeckosBtcBasedRates() {
        CoinGeckoExchangeRateSnapshot snapshot = client().parseExchangeRateResponse("""
                {
                  "rates": {
                    "usd": {"value": 100},
                    "gbp": {"value": 80},
                    "cad": {"value": 140},
                    "eur": {"value": 90},
                    "sgd": {"value": 130},
                    "jpy": {"value": 15000},
                    "aud": {"value": 150},
                    "aed": {"value": 367},
                    "chf": {"value": 85},
                    "php": {"value": 5800}
                  }
                }
                """);

        assertEquals(0, BigDecimal.ONE.compareTo(snapshot.fiatPerUsd().get(FiatCurrency.USD)));
        assertEquals(0, new BigDecimal("58.000000000000000000")
                .compareTo(snapshot.fiatPerUsd().get(FiatCurrency.PHP)));
        assertEquals(NOW, snapshot.timestamp());
    }

    @Test
    void clientParsesHistoricalUsdPricesForColdStartSeeding() {
        MarketPriceHistory history = client().parseHistoryResponse("""
                {
                  "prices": [
                    [1788176400000, 0.9991],
                    [1788180000000, 1.0002]
                  ]
                }
                """, Asset.USDT, FiatCurrency.USD, PriceHistoryRange.NINETY_DAYS);

        assertEquals(2, history.points().size());
        assertEquals(0, new BigDecimal("1.0002").compareTo(history.points().get(1).price()));
        assertEquals(PriceSource.COINGECKO, history.source());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void clientSurfacesRateLimitsWithoutSleepingOrRetrying() throws Exception {
        PricingConfigurationProperties configuration = configuration();
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> rateLimited = mock(HttpResponse.class);
        when(rateLimited.statusCode()).thenReturn(429);
        when(rateLimited.headers()).thenReturn(HttpHeaders.of(
                Map.of("Retry-After", List.of("7")), (name, value) -> true));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(rateLimited);
        CoinGeckoMarketDataClient client = new CoinGeckoMarketDataClient(
                new ObjectMapper(),
                configuration,
                httpClient,
                Clock.fixed(NOW, ZoneOffset.UTC));

        CoinGeckoRateLimitException exception = assertThrows(
                CoinGeckoRateLimitException.class,
                client::fetchUsdPrices);

        assertEquals(7000, exception.getRetryAfterMs());
    }

    @Test
    void priceServiceReadsTheLatestStoredUsdPoints() {
        MarketPricePointRepository repository = mock(MarketPricePointRepository.class);
        when(repository.findFirstByAssetAndQuoteCurrencyOrderByTimestampDesc(
                        Asset.BTC, FiatCurrency.USD))
                .thenReturn(Optional.of(point(Asset.BTC, "68000", NOW.minusSeconds(30))));
        when(repository.findFirstByAssetAndQuoteCurrencyOrderByTimestampDesc(
                        Asset.ETH, FiatCurrency.USD))
                .thenReturn(Optional.of(point(Asset.ETH, "3500", NOW.minusSeconds(20))));
        when(repository.findFirstByAssetAndQuoteCurrencyOrderByTimestampDesc(
                        Asset.USDT, FiatCurrency.USD))
                .thenReturn(Optional.of(point(Asset.USDT, "0.9999", NOW.minusSeconds(10))));
        CoinGeckoPriceService service = new CoinGeckoPriceService(
                repository,
                configuration(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertEquals(0, new BigDecimal("68000").compareTo(service.getUsdPrice(Asset.BTC)));
        assertEquals(0, new BigDecimal("3500").compareTo(service.getUsdPrice(Asset.ETH)));
        assertEquals(0, new BigDecimal("0.9999").compareTo(service.getUsdPrice(Asset.USDT)));
        assertEquals(PriceSource.COINGECKO, service.getStatus().source());
        assertEquals(NOW.minusSeconds(30), service.getStatus().updatedAt());
        assertFalse(service.getStatus().stale());
    }

    @Test
    void priceServiceUsesConfiguredValuesOnlyWhenStoredPointsAreAbsent() {
        MarketPricePointRepository repository = mock(MarketPricePointRepository.class);
        when(repository.findFirstByAssetAndQuoteCurrencyOrderByTimestampDesc(
                        any(Asset.class), any(FiatCurrency.class)))
                .thenReturn(Optional.empty());
        CoinGeckoPriceService service = new CoinGeckoPriceService(
                repository,
                configuration(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertEquals(0, new BigDecimal("60000").compareTo(service.getUsdPrice(Asset.BTC)));
        assertEquals(PriceSource.STATIC_FALLBACK, service.getStatus().source());
        assertNull(service.getStatus().updatedAt());
        assertTrue(service.getStatus().stale());
    }

    private CoinGeckoMarketDataClient client() {
        return new CoinGeckoMarketDataClient(
                new ObjectMapper(),
                configuration(),
                HttpClient.newHttpClient(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private MarketPricePoint point(Asset asset, String price, Instant timestamp) {
        MarketPricePoint point = new MarketPricePoint();
        point.setAsset(asset);
        point.setQuoteCurrency(FiatCurrency.USD);
        point.setTimestamp(timestamp);
        point.setPrice(new BigDecimal(price));
        point.setSource(PriceSource.COINGECKO);
        return point;
    }

    private PricingConfigurationProperties configuration() {
        PricingConfigurationProperties configuration = new PricingConfigurationProperties();
        configuration.setProvider("coingecko");
        configuration.setBaseUrl("https://api.coingecko.com/api/v3");
        configuration.setRefreshMs(300000);
        configuration.setConnectTimeoutMs(3000);
        configuration.setRequestTimeoutMs(10000);
        configuration.setRateLimitInitialBackoffMs(2000);
        configuration.setRateLimitMaxRetries(3);
        configuration.setFallbackUsdPrices(Map.of(
                Asset.BTC, new BigDecimal("60000"),
                Asset.ETH, new BigDecimal("3000"),
                Asset.USDT, BigDecimal.ONE));
        return configuration;
    }
}
