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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "wallet_balances",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_wallet_balances_user_asset",
                columnNames = {"user_id", "asset"}),
        indexes = @Index(name = "idx_wallet_balances_user_id", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WalletBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_wallet_balances_user"))
    private User user;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Asset asset;

    @NotNull
    @PositiveOrZero
    @Column(name = "available_amount", nullable = false, precision = 38, scale = 18)
    private BigDecimal availableAmount;

    @NotNull
    @PositiveOrZero
    @Column(name = "locked_amount", nullable = false, precision = 38, scale = 18)
    private BigDecimal lockedAmount;
}
