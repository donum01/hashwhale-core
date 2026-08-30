package com.hashwhale.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Successful authentication result")
public record AuthResponse(
        @Schema(description = "Bearer token valid for 24 hours", example = "eyJhbGciOiJIUzI1NiJ9...")
        String token) {
}
