package com.hashwhale.core.dto;

import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.TransactionStatus;
import com.hashwhale.core.entity.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Wallet or account transaction")
public record TransactionResponse(
        @Schema(example = "DEPOSIT") TransactionType type,
        @Schema(example = "USDT") Asset asset,
        @Schema(example = "250.00") BigDecimal amount,
        @Schema(example = "COMPLETED") TransactionStatus status,
        @Schema(example = "2026-08-31T07:30:00Z") Instant createdAt) {
}
