package com.hashwhale.core.repository;

import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.FiatCurrency;
import com.hashwhale.core.entity.MarketPricePoint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketPricePointRepository extends JpaRepository<MarketPricePoint, Long> {

    long countByAsset(Asset asset);

    boolean existsByAssetAndQuoteCurrencyAndTimestamp(
            Asset asset, FiatCurrency quoteCurrency, Instant timestamp);

    Optional<MarketPricePoint> findFirstByAssetAndQuoteCurrencyOrderByTimestampDesc(
            Asset asset, FiatCurrency quoteCurrency);

    List<MarketPricePoint> findByAssetAndQuoteCurrencyAndTimestampBetweenOrderByTimestampAsc(
            Asset asset, FiatCurrency quoteCurrency, Instant from, Instant to);
}
