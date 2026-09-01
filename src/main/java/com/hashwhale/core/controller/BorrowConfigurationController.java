package com.hashwhale.core.controller;

import com.hashwhale.core.config.BorrowConfigurationProperties;
import com.hashwhale.core.dto.ApiErrorResponse;
import com.hashwhale.core.dto.BorrowConfigurationResponse;
import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.service.PriceService;
import com.hashwhale.core.service.PriceServiceStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/borrow")
@Tag(name = "Borrow", description = "Create, repay, and view collateralized loans")
public class BorrowConfigurationController {

    private final PriceService priceService;
    private final BorrowConfigurationProperties configuration;

    @GetMapping("/configuration")
    @Operation(
            summary = "Get borrow configuration",
            description = "Returns the prices and risk parameters currently used by the borrow engine.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Configuration returned",
                    content = @Content(schema = @Schema(implementation = BorrowConfigurationResponse.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<BorrowConfigurationResponse> getConfiguration() {
        Map<Asset, BigDecimal> prices = new EnumMap<>(Asset.class);
        for (Asset asset : Asset.values()) {
            prices.put(asset, priceService.getUsdPrice(asset));
        }
        PriceServiceStatus priceStatus = priceService.getStatus();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new BorrowConfigurationResponse(
                        Map.copyOf(prices),
                        priceStatus.source(),
                        priceStatus.updatedAt(),
                        priceStatus.stale(),
                        configuration.getInterestRateApr(),
                        configuration.getMaxLtvPercent(),
                        configuration.getWarningLtvPercent(),
                        configuration.getLiquidationLtvPercent()));
    }
}
