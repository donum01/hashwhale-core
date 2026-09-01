package com.hashwhale.core.service;

public class CoinGeckoRateLimitException extends RuntimeException {

    private final long retryAfterMs;

    public CoinGeckoRateLimitException(long retryAfterMs) {
        super("CoinGecko rate limit exceeded");
        this.retryAfterMs = Math.max(0L, retryAfterMs);
    }

    public long getRetryAfterMs() {
        return retryAfterMs;
    }
}
