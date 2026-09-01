package com.hashwhale.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.PriceSource;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "app.pricing.provider=coingecko",
        "app.pricing.collector-enabled=false",
        "app.pricing.base-url=http://127.0.0.1:1",
        "app.pricing.connect-timeout-ms=100",
        "app.pricing.request-timeout-ms=100"
})
class CoinGeckoPriceConfigurationIntegrationTest {

    @Autowired
    private PriceService priceService;

    @Test
    void coinGeckoProviderStartsAndFallsBackWhenProviderIsUnavailable() {
        assertEquals(PriceSource.STATIC_FALLBACK, priceService.getStatus().source());
        assertTrue(priceService.getStatus().stale());
        assertEquals(0, new BigDecimal("60000").compareTo(priceService.getUsdPrice(Asset.BTC)));
        assertEquals(0, new BigDecimal("3000").compareTo(priceService.getUsdPrice(Asset.ETH)));
        assertEquals(0, BigDecimal.ONE.compareTo(priceService.getUsdPrice(Asset.USDT)));
    }
}
