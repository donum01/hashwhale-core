package com.hashwhale.core.controller;

import com.hashwhale.core.dto.ApiErrorResponse;
import com.hashwhale.core.dto.WalletBalanceResponse;
import com.hashwhale.core.repository.WalletBalanceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/wallet")
@Tag(name = "Wallet", description = "View asset balances")
public class WalletController {

    private final WalletBalanceRepository walletBalanceRepository;

    @GetMapping("/{userId}/balances")
    @Operation(summary = "List a user's wallet balances")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Balances returned",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = WalletBalanceResponse.class)))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid user id",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<List<WalletBalanceResponse>> getBalances(@PathVariable @Positive Long userId) {
        List<WalletBalanceResponse> balances = walletBalanceRepository.findByUserId(userId)
                .stream()
                .map(balance -> new WalletBalanceResponse(
                        balance.getAsset(),
                        balance.getAvailableAmount(),
                        balance.getLockedAmount()))
                .toList();
        return ResponseEntity.ok(balances);
    }
}
