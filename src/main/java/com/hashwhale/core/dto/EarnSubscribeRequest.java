package com.hashwhale.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Simulated Earn subscription")
public class EarnSubscribeRequest {

    @NotBlank
    @Schema(example = "USDT_LOCKED_90")
    private String productId;

    @NotNull
    @Positive
    @Schema(example = "1000")
    private BigDecimal amount;
}
