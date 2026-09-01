package com.hashwhale.core.service;

import java.time.Duration;
import java.util.Arrays;

public enum PriceHistoryRange {
    ONE_DAY("1D", "1", Duration.ofDays(1)),
    SEVEN_DAYS("7D", "7", Duration.ofDays(7)),
    THIRTY_DAYS("30D", "30", Duration.ofDays(30)),
    NINETY_DAYS("90D", "90", Duration.ofDays(90));

    private final String apiValue;
    private final String providerDays;
    private final Duration duration;

    PriceHistoryRange(String apiValue, String providerDays, Duration duration) {
        this.apiValue = apiValue;
        this.providerDays = providerDays;
        this.duration = duration;
    }

    public String apiValue() {
        return apiValue;
    }

    public String providerDays() {
        return providerDays;
    }

    public Duration duration() {
        return duration;
    }

    public static PriceHistoryRange fromApiValue(String value) {
        return Arrays.stream(values())
                .filter(range -> range.apiValue.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported price range. Use 1D, 7D, 30D, or 90D"));
    }
}
