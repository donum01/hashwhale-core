package com.hashwhale.core.service;

import com.hashwhale.core.entity.Asset;
import java.math.BigDecimal;

/**
 * Provides the current USD price of an asset.
 *
 * <p>A production market-data adapter can replace the static implementation without changing
 * borrowing logic.
 */
public interface PriceService {

    BigDecimal getUsdPrice(Asset asset);

    PriceServiceStatus getStatus();
}
