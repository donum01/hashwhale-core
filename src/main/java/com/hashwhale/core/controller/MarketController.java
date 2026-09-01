package com.hashwhale.core.controller;

import com.hashwhale.core.dto.ApiErrorResponse;
import com.hashwhale.core.dto.MarketPriceHistoryResponse;
import com.hashwhale.core.dto.MarketPricePointResponse;
import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.FiatCurrency;
import com.hashwhale.core.entity.User;
import com.hashwhale.core.service.MarketHistoryService;
import com.hashwhale.core.service.MarketPriceHistory;
import com.hashwhale.core.service.MarketPriceSample;
import com.hashwhale.core.service.PriceHistoryRange;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/market")
@Tag(name = "Market", description = "Authenticated historical market data")
public class MarketController {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int CALCULATION_SCALE = 8;

    private final MarketHistoryService marketHistoryService;

    @GetMapping("/prices/{asset}/history")
    @Operation(
            summary = "Get historical prices",
            description = "BTC and ETH default to USD. USDT defaults to the authenticated user's country-derived fiat currency.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Historical prices returned",
                    content = @Content(schema = @Schema(implementation = MarketPriceHistoryResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Unsupported asset, range, or quote currency",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired JWT"),
            @ApiResponse(
                    responseCode = "503",
                    description = "Insufficient locally stored history is currently available",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<MarketPriceHistoryResponse> getPriceHistory(
            @PathVariable Asset asset,
            @RequestParam(defaultValue = "7D") String range,
            @RequestParam(required = false) FiatCurrency currency) {
        User user = authenticatedUser();
        FiatCurrency quoteCurrency = currency != null
                ? currency
                : asset == Asset.USDT ? user.getPreferredFiatCurrency() : FiatCurrency.USD;
        MarketPriceHistory history = marketHistoryService.getHistory(
                asset, quoteCurrency, PriceHistoryRange.fromApiValue(range));

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(toResponse(history));
    }

    private MarketPriceHistoryResponse toResponse(MarketPriceHistory history) {
        List<MarketPriceSample> points = history.points();
        BigDecimal first = points.get(0).price();
        BigDecimal current = points.get(points.size() - 1).price();
        BigDecimal changeAmount = current.subtract(first);
        BigDecimal changePercent = first.signum() == 0
                ? BigDecimal.ZERO
                : changeAmount.multiply(ONE_HUNDRED)
                        .divide(first, CALCULATION_SCALE, RoundingMode.HALF_UP);
        BigDecimal minimum = points.stream()
                .map(MarketPriceSample::price)
                .min(Comparator.naturalOrder())
                .orElse(current);
        BigDecimal maximum = points.stream()
                .map(MarketPriceSample::price)
                .max(Comparator.naturalOrder())
                .orElse(current);

        return new MarketPriceHistoryResponse(
                history.asset(),
                history.quoteCurrency(),
                history.range().apiValue(),
                current,
                changeAmount,
                changePercent,
                minimum,
                maximum,
                history.source(),
                history.updatedAt(),
                history.stale(),
                points.stream()
                        .map(point -> new MarketPricePointResponse(point.timestamp(), point.price()))
                        .toList());
    }

    private User authenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            throw new IllegalStateException("Authenticated user is unavailable");
        }
        return user;
    }
}
