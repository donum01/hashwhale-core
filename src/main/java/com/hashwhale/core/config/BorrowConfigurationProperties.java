package com.hashwhale.core.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "app.borrow")
public class BorrowConfigurationProperties {

    @NotNull
    @PositiveOrZero
    private BigDecimal interestRateApr;

    @NotNull
    @Positive
    @DecimalMax("100")
    private BigDecimal maxLtvPercent;

    @NotNull
    @Positive
    @DecimalMax("100")
    private BigDecimal warningLtvPercent;

    @NotNull
    @Positive
    @DecimalMax("100")
    private BigDecimal liquidationLtvPercent;

    @AssertTrue(message = "app.borrow.warning-ltv-percent must not exceed max-ltv-percent")
    public boolean isWarningLtvAtOrBelowMaximumLtv() {
        return warningLtvPercent == null
                || maxLtvPercent == null
                || warningLtvPercent.compareTo(maxLtvPercent) <= 0;
    }

    @AssertTrue(message = "app.borrow.liquidation-ltv-percent must exceed max-ltv-percent")
    public boolean isLiquidationLtvAboveMaximumLtv() {
        return maxLtvPercent == null
                || liquidationLtvPercent == null
                || liquidationLtvPercent.compareTo(maxLtvPercent) > 0;
    }
}
