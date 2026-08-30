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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "earn_positions",
        indexes = @Index(name = "idx_earn_positions_user_id", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EarnPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_earn_positions_user"))
    private User user;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Asset asset;

    @NotNull
    @Positive
    @Column(name = "principal_amount", nullable = false, precision = 38, scale = 18)
    private BigDecimal principalAmount;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal apy;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "term_type", nullable = false, length = 20)
    private EarnTermType termType;

    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EarnPositionStatus status = EarnPositionStatus.ACTIVE;
}
