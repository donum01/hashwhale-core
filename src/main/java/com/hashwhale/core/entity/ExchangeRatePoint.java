package com.hashwhale.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A timestamped quote expressed as units of {@code quoteCurrency} per one USD. */
@Entity
@Table(
        name = "exchange_rate_points",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_exchange_rate_quote_timestamp",
                columnNames = {"quote_currency", "rate_timestamp"}),
        indexes = @Index(
                name = "idx_exchange_rate_quote_timestamp",
                columnList = "quote_currency, rate_timestamp"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRatePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "quote_currency", nullable = false, length = 10)
    private FiatCurrency quoteCurrency;

    @NotNull
    @Column(name = "rate_timestamp", nullable = false)
    private Instant timestamp;

    @NotNull
    @Positive
    @Column(name = "fiat_per_usd", nullable = false, precision = 38, scale = 18)
    private BigDecimal fiatPerUsd;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PriceSource source;
}
