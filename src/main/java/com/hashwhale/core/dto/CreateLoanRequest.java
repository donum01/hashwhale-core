package com.hashwhale.core.dto;

import com.hashwhale.core.entity.Asset;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Parameters for creating a collateralized USDT loan")
public class CreateLoanRequest {

    @NotNull
    @Schema(description = "Asset to lock as collateral", example = "BTC")
    private Asset collateralAsset;

    @NotNull
    @Positive
    @Schema(description = "Amount of collateral to lock", example = "0.50000000")
    private BigDecimal collateralAmount;

    @NotNull
    @Positive
    @Schema(description = "USDT amount to borrow", example = "15000.00")
    private BigDecimal borrowedAmount;
}
