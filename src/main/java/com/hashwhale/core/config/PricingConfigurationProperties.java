package com.hashwhale.core.config;

import com.hashwhale.core.entity.Asset;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "app.pricing")
public class PricingConfigurationProperties {

    @NotBlank
    private String provider;

    @NotBlank
    private String baseUrl;
    private String apiKey;
    private boolean collectorEnabled = true;

    @Positive
    private long refreshMs;

    @Positive
    private long connectTimeoutMs;

    @Positive
    private long requestTimeoutMs;

    @Positive
    private long rateLimitInitialBackoffMs;

    @PositiveOrZero
    private int rateLimitMaxRetries;

    @NotEmpty
    private Map<Asset, @NotNull @Positive BigDecimal> fallbackUsdPrices = new EnumMap<>(Asset.class);

    @AssertTrue(message = "app.pricing.fallback-usd-prices must contain every supported asset")
    public boolean isEveryAssetPriced() {
        return fallbackUsdPrices != null
                && fallbackUsdPrices.keySet().containsAll(EnumSet.allOf(Asset.class));
    }

}
