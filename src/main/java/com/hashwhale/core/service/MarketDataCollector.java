package com.hashwhale.core.service;

import com.hashwhale.core.config.PricingConfigurationProperties;
import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.FiatCurrency;
import com.hashwhale.core.repository.ExchangeRatePointRepository;
import com.hashwhale.core.repository.MarketPricePointRepository;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression(
        "'${app.pricing.provider:static}' == 'coingecko' and ${app.pricing.collector-enabled:true}")
public class MarketDataCollector implements ApplicationRunner {

    private static final long MAX_BACKOFF_MS = 60000L;

    private final CoinGeckoMarketDataClient marketDataClient;
    private final MarketDataPersistenceService persistenceService;
    private final MarketPricePointRepository marketPricePointRepository;
    private final ExchangeRatePointRepository exchangeRatePointRepository;
    private final PricingConfigurationProperties configuration;
    private final AtomicBoolean collectionInProgress = new AtomicBoolean();

    @Override
    public void run(ApplicationArguments args) {
        if (!collectionInProgress.compareAndSet(false, true)) {
            return;
        }
        try {
            seedMissingHistoryBeforeReady();
            seedExchangeRatesBeforeReady();
        } catch (RuntimeException exception) {
            log.warn(
                    "Market-data startup seed failed; the application will continue starting. "
                            + "Market endpoints may return 503 until a scheduled collection succeeds: {}",
                    exception.getMessage(),
                    exception);
        } finally {
            collectionInProgress.set(false);
        }
    }

    @Scheduled(
            fixedDelayString = "${app.pricing.refresh-ms:300000}",
            initialDelayString = "${app.pricing.refresh-ms:300000}")
    public void collectLatestPoints() {
        if (!collectionInProgress.compareAndSet(false, true)) {
            return;
        }
        try {
            CoinGeckoPriceSnapshot prices = withRateLimitBackoff(
                    marketDataClient::fetchUsdPrices);
            persistenceService.appendCurrentPrices(prices);

            CoinGeckoExchangeRateSnapshot exchangeRates = withRateLimitBackoff(
                    marketDataClient::fetchFiatPerUsdRates);
            persistenceService.appendExchangeRates(exchangeRates);
        } catch (RuntimeException exception) {
            log.warn("Market-data collection failed; locally stored history remains available: {}",
                    exception.getMessage());
        } finally {
            collectionInProgress.set(false);
        }
    }

    private void seedMissingHistoryBeforeReady() {
        for (Asset asset : Asset.values()) {
            if (marketPricePointRepository.countByAsset(asset) > 0) {
                continue;
            }
            log.info("No stored {} history found; seeding 90 days before accepting requests", asset);
            MarketPriceHistory history = withRateLimitBackoff(() ->
                    marketDataClient.fetchPriceHistory(
                            asset, FiatCurrency.USD, PriceHistoryRange.NINETY_DAYS));
            persistenceService.seedUsdHistory(asset, history.points());
            log.info("Seeded {} {} price points", history.points().size(), asset);
        }
    }

    private void seedExchangeRatesBeforeReady() {
        boolean missingCurrency = Arrays.stream(FiatCurrency.values())
                .anyMatch(currency -> exchangeRatePointRepository.countByQuoteCurrency(currency) == 0);
        if (!missingCurrency) {
            return;
        }
        log.info("Missing stored fiat conversion rates; seeding current rates before accepting requests");
        persistenceService.appendExchangeRates(withRateLimitBackoff(
                marketDataClient::fetchFiatPerUsdRates));
    }

    private <T> T withRateLimitBackoff(Supplier<T> operation) {
        long fallbackDelayMs = configuration.getRateLimitInitialBackoffMs();
        for (int attempt = 0; ; attempt++) {
            try {
                return operation.get();
            } catch (CoinGeckoRateLimitException exception) {
                if (attempt >= configuration.getRateLimitMaxRetries()) {
                    throw exception;
                }
                long delayMs = Math.min(
                        Math.max(exception.getRetryAfterMs(), fallbackDelayMs),
                        MAX_BACKOFF_MS);
                log.warn("CoinGecko rate limited collection; retrying in {} ms", delayMs);
                sleep(delayMs);
                fallbackDelayMs = Math.min(fallbackDelayMs * 2, MAX_BACKOFF_MS);
            }
        }
    }

    private void sleep(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Market-data collection was interrupted", exception);
        }
    }
}
