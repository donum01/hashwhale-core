package com.hashwhale.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hashwhale.core.config.PricingConfigurationProperties;
import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.FiatCurrency;
import com.hashwhale.core.entity.PriceSource;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.pricing.provider", havingValue = "coingecko")
public class CoinGeckoMarketDataClient {

    private static final String SIMPLE_PRICE_PATH =
            "/simple/price?ids=bitcoin%2Cethereum%2Ctether&vs_currencies=usd&include_last_updated_at=true";
    private static final String EXCHANGE_RATE_PATH = "/exchange_rates";
    private static final int RATE_SCALE = 18;

    private final ObjectMapper objectMapper;
    private final PricingConfigurationProperties configuration;
    private final HttpClient httpClient;
    private final Clock clock;

    @Autowired
    public CoinGeckoMarketDataClient(PricingConfigurationProperties configuration) {
        this(
                new ObjectMapper(),
                configuration,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(configuration.getConnectTimeoutMs()))
                        .build(),
                Clock.systemUTC());
    }

    CoinGeckoMarketDataClient(
            ObjectMapper objectMapper,
            PricingConfigurationProperties configuration,
            HttpClient httpClient) {
        this(objectMapper, configuration, httpClient, Clock.systemUTC());
    }

    CoinGeckoMarketDataClient(
            ObjectMapper objectMapper,
            PricingConfigurationProperties configuration,
            HttpClient httpClient,
            Clock clock) {
        this.objectMapper = objectMapper;
        this.configuration = configuration;
        this.httpClient = httpClient;
        this.clock = clock;
    }

    public CoinGeckoPriceSnapshot fetchUsdPrices() {
        try {
            HttpResponse<String> response = send(requestBuilder(buildSimplePriceUri()).build());
            return parsePriceResponse(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("CoinGecko request was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("CoinGecko request failed", exception);
        }
    }

    public CoinGeckoExchangeRateSnapshot fetchFiatPerUsdRates() {
        try {
            HttpResponse<String> response = send(requestBuilder(buildExchangeRateUri()).build());
            return parseExchangeRateResponse(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("CoinGecko exchange-rate request was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("CoinGecko exchange-rate request failed", exception);
        }
    }

    public MarketPriceHistory fetchPriceHistory(
            Asset asset, FiatCurrency quoteCurrency, PriceHistoryRange range) {
        try {
            HttpResponse<String> response = send(
                    requestBuilder(buildHistoryUri(asset, quoteCurrency, range)).build());
            return parseHistoryResponse(response.body(), asset, quoteCurrency, range);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("CoinGecko history request was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("CoinGecko history request failed", exception);
        }
    }

    private HttpRequest.Builder requestBuilder(URI uri) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(configuration.getRequestTimeoutMs()))
                .header("Accept", "application/json")
                .GET();
        if (configuration.getApiKey() != null && !configuration.getApiKey().isBlank()) {
            requestBuilder.header("x-cg-demo-api-key", configuration.getApiKey());
        }
        return requestBuilder;
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 429) {
            long retryAfterMs = response.headers()
                    .firstValue("Retry-After")
                    .flatMap(this::parseRetryAfterSeconds)
                    .map(seconds -> seconds * 1000L)
                    .orElse(configuration.getRateLimitInitialBackoffMs());
            throw new CoinGeckoRateLimitException(retryAfterMs);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "CoinGecko returned HTTP status " + response.statusCode());
        }
        return response;
    }

    CoinGeckoPriceSnapshot parsePriceResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            Map<Asset, MarketPriceSample> points = new EnumMap<>(Asset.class);
            addCurrentPoint(points, root, Asset.BTC, "bitcoin");
            addCurrentPoint(points, root, Asset.ETH, "ethereum");
            addCurrentPoint(points, root, Asset.USDT, "tether");
            return new CoinGeckoPriceSnapshot(points);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("CoinGecko returned invalid JSON", exception);
        }
    }

    CoinGeckoExchangeRateSnapshot parseExchangeRateResponse(String responseBody) {
        try {
            JsonNode rates = objectMapper.readTree(responseBody).path("rates");
            BigDecimal usdPerBitcoin = requiredPositiveDecimal(rates, "usd", "value");
            Map<FiatCurrency, BigDecimal> fiatPerUsd = new EnumMap<>(FiatCurrency.class);
            for (FiatCurrency currency : FiatCurrency.values()) {
                BigDecimal rate = currency == FiatCurrency.USD
                        ? BigDecimal.ONE
                        : requiredPositiveDecimal(
                                        rates, currency.name().toLowerCase(), "value")
                                .divide(usdPerBitcoin, RATE_SCALE, RoundingMode.HALF_UP);
                fiatPerUsd.put(currency, rate);
            }
            return new CoinGeckoExchangeRateSnapshot(fiatPerUsd, clock.instant());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("CoinGecko returned invalid exchange-rate JSON", exception);
        }
    }

    MarketPriceHistory parseHistoryResponse(
            String responseBody,
            Asset asset,
            FiatCurrency quoteCurrency,
            PriceHistoryRange range) {
        try {
            JsonNode pricesNode = objectMapper.readTree(responseBody).path("prices");
            if (!pricesNode.isArray()) {
                throw new IllegalStateException("CoinGecko history response is missing prices");
            }

            List<MarketPriceSample> points = new ArrayList<>();
            for (JsonNode entry : pricesNode) {
                if (!entry.isArray() || entry.size() < 2) {
                    continue;
                }
                JsonNode timestampNode = entry.get(0);
                JsonNode priceNode = entry.get(1);
                if (timestampNode.canConvertToLong()
                        && timestampNode.longValue() > 0
                        && priceNode.isNumber()
                        && priceNode.decimalValue().signum() > 0) {
                    points.add(new MarketPriceSample(
                            Instant.ofEpochMilli(timestampNode.longValue()),
                            priceNode.decimalValue()));
                }
            }
            if (points.size() < 2) {
                throw new IllegalStateException("CoinGecko returned insufficient price history");
            }
            return new MarketPriceHistory(
                    asset,
                    quoteCurrency,
                    range,
                    List.copyOf(points),
                    PriceSource.COINGECKO,
                    points.get(points.size() - 1).timestamp(),
                    false);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("CoinGecko returned invalid history JSON", exception);
        }
    }

    private void addCurrentPoint(
            Map<Asset, MarketPriceSample> points,
            JsonNode root,
            Asset asset,
            String providerId) {
        points.put(asset, new MarketPriceSample(
                Instant.ofEpochSecond(requiredPositiveLong(root, providerId, "last_updated_at")),
                requiredPositiveDecimal(root, providerId, "usd")));
    }

    private BigDecimal requiredPositiveDecimal(JsonNode root, String object, String field) {
        JsonNode value = root.path(object).path(field);
        if (!value.isNumber() || value.decimalValue().signum() <= 0) {
            throw new IllegalStateException(
                    "CoinGecko response is missing a positive " + object + " " + field);
        }
        return value.decimalValue();
    }

    private long requiredPositiveLong(JsonNode root, String object, String field) {
        JsonNode value = root.path(object).path(field);
        if (!value.canConvertToLong() || value.longValue() <= 0) {
            throw new IllegalStateException(
                    "CoinGecko response is missing a positive " + object + " " + field);
        }
        return value.longValue();
    }

    private Optional<Long> parseRetryAfterSeconds(String value) {
        try {
            return Optional.of(Long.parseLong(value.trim()));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private URI buildSimplePriceUri() {
        return URI.create(baseUrl() + SIMPLE_PRICE_PATH);
    }

    private URI buildExchangeRateUri() {
        return URI.create(baseUrl() + EXCHANGE_RATE_PATH);
    }

    private URI buildHistoryUri(
            Asset asset, FiatCurrency quoteCurrency, PriceHistoryRange range) {
        String assetId = switch (asset) {
            case BTC -> "bitcoin";
            case ETH -> "ethereum";
            case USDT -> "tether";
        };
        return URI.create(baseUrl()
                + "/coins/" + assetId
                + "/market_chart?vs_currency=" + quoteCurrency.name().toLowerCase()
                + "&days=" + range.providerDays()
                + "&precision=full");
    }

    private String baseUrl() {
        return configuration.getBaseUrl().replaceAll("/+$", "");
    }
}
