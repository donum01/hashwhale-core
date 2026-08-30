package com.hashwhale.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "loans", indexes = @Index(name = "idx_loans_user_id", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_loans_user"))
    private User user;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "collateral_asset", nullable = false, length = 10)
    private Asset collateralAsset;

    @NotNull
    @Positive
    @Column(name = "collateral_amount", nullable = false, precision = 38, scale = 18)
    private BigDecimal collateralAmount;

    @NotNull
    @Positive
    @Column(name = "borrowed_amount", nullable = false, precision = 38, scale = 18)
    private BigDecimal borrowedAmount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "borrowed_asset", nullable = false, length = 10)
    private Asset borrowedAsset;

    @NotNull
    @PositiveOrZero
    @Column(name = "interest_rate_apr", nullable = false, precision = 38, scale = 18)
    private BigDecimal interestRateApr;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoanStatus status = LoanStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void initializeCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
