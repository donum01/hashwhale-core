package com.hashwhale.core.repository;

import com.hashwhale.core.entity.ExchangeRatePoint;
import com.hashwhale.core.entity.FiatCurrency;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRatePointRepository extends JpaRepository<ExchangeRatePoint, Long> {

    long countByQuoteCurrency(FiatCurrency quoteCurrency);

    boolean existsByQuoteCurrencyAndTimestamp(FiatCurrency quoteCurrency, Instant timestamp);

    Optional<ExchangeRatePoint> findFirstByQuoteCurrencyOrderByTimestampDesc(
            FiatCurrency quoteCurrency);

    Optional<ExchangeRatePoint> findFirstByQuoteCurrencyAndTimestampLessThanEqualOrderByTimestampDesc(
            FiatCurrency quoteCurrency, Instant timestamp);

    Optional<ExchangeRatePoint> findFirstByQuoteCurrencyAndTimestampGreaterThanOrderByTimestampAsc(
            FiatCurrency quoteCurrency, Instant timestamp);

    List<ExchangeRatePoint> findByQuoteCurrencyAndTimestampBetweenOrderByTimestampAsc(
            FiatCurrency quoteCurrency, Instant from, Instant to);
}
