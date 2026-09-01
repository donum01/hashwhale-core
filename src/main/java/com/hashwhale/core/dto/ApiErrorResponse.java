package com.hashwhale.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Standard API error response")
public record ApiErrorResponse(
        @Schema(example = "2026-08-30T12:00:00Z") Instant timestamp,
        @Schema(example = "400") int status,
        @Schema(example = "Bad Request") String error,
        @Schema(example = "Insufficient available collateral balance") String message,
        @Schema(example = "/api/borrow/loans") String path) {
}
