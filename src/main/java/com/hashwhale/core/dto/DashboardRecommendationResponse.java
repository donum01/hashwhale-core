package com.hashwhale.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One deterministic, non-advisory next action")
public record DashboardRecommendationResponse(
        @Schema(example = "Put an idle balance to work") String title,
        String message,
        @Schema(example = "/earn") String href,
        @Schema(example = "Explore Earn") String actionLabel) {}
