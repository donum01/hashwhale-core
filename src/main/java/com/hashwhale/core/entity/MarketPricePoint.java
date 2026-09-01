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

@Entity
@Table(
        name = "market_price_points",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_market_price_asset_quote_timestamp",
                columnNames = {"asset", "quote_currency", "price_timestamp"}),
        indexes = @Index(
                name = "idx_market_price_asset_quote_timestamp",
                columnList = "asset, quote_currency, price_timestamp"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MarketPricePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Asset asset;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "quote_currency", nullable = false, length = 10)
    private FiatCurrency quoteCurrency;

    @NotNull
    @Column(name = "price_timestamp", nullable = false)
    private Instant timestamp;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal price;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PriceSource source;
}
