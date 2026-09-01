package com.hashwhale.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Actionable account alert")
public record DashboardAlertResponse(
        DashboardAlertSeverity severity,
        @Schema(example = "Loan LTV needs attention") String title,
        @Schema(example = "Your highest active-loan LTV is 66.2%.") String message,
        @Schema(example = "/borrow", nullable = true) String href,
        @Schema(example = "Review loans", nullable = true) String actionLabel) {}
