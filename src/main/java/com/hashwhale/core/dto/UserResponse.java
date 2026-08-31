package com.hashwhale.core.dto;

import com.hashwhale.core.entity.KycStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Authenticated user profile")
public record UserResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "satoshi@example.com") String email,
        @Schema(example = "VERIFIED") KycStatus kycStatus,
        @Schema(example = "2026-08-30T12:00:00Z") Instant createdAt) {
}
