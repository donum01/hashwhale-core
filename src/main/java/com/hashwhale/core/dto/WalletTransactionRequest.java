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
@Schema(description = "Wallet deposit or withdrawal request")
public class WalletTransactionRequest {

    @NotNull
    @Schema(description = "Asset to deposit or withdraw", example = "USDT")
    private Asset asset;

    @NotNull
    @Positive
    @Schema(description = "Positive asset amount", example = "250.00")
    private BigDecimal amount;
}
