package com.hashwhale.core.controller;

import com.hashwhale.core.dto.ApiErrorResponse;
import com.hashwhale.core.dto.TransactionResponse;
import com.hashwhale.core.dto.WalletBalanceResponse;
import com.hashwhale.core.dto.WalletTransactionRequest;
import com.hashwhale.core.entity.Transaction;
import com.hashwhale.core.entity.User;
import com.hashwhale.core.entity.WalletBalance;
import com.hashwhale.core.service.ForbiddenException;
import com.hashwhale.core.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/wallet")
@Tag(name = "Wallet", description = "View and manage asset balances")
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/balances")
    @Operation(summary = "List the authenticated user's wallet balances")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Balances returned",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = WalletBalanceResponse.class)))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<List<WalletBalanceResponse>> getBalances() {
        User user = getAuthenticatedUser();
        List<WalletBalanceResponse> balances = walletService.getBalances(user.getId())
                .stream()
                .map(this::toBalanceResponse)
                .toList();
        return ResponseEntity.ok(balances);
    }

    @PostMapping("/deposit")
    @Operation(
            summary = "Simulate a wallet deposit",
            description = "Updates the internal demo ledger only; no blockchain transaction or real asset transfer occurs.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Simulated deposit completed and demo balance updated",
                    content = @Content(schema = @Schema(implementation = WalletBalanceResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid asset or amount",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired JWT")
    })
    public ResponseEntity<WalletBalanceResponse> deposit(
            @Valid @RequestBody WalletTransactionRequest request) {
        User user = getAuthenticatedUser();
        WalletBalance balance = walletService.deposit(
                user.getId(), request.getAsset(), request.getAmount());
        return ResponseEntity.ok(toBalanceResponse(balance));
    }

    @PostMapping("/withdraw")
    @Operation(
            summary = "Simulate a wallet withdrawal",
            description = "Updates the internal demo ledger only; no blockchain transaction or real asset transfer occurs.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Simulated withdrawal completed and demo balance updated",
                    content = @Content(schema = @Schema(implementation = WalletBalanceResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid input or insufficient available balance",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired JWT")
    })
    public ResponseEntity<WalletBalanceResponse> withdraw(
            @Valid @RequestBody WalletTransactionRequest request) {
        User user = getAuthenticatedUser();
        WalletBalance balance = walletService.withdraw(
                user.getId(), request.getAsset(), request.getAmount());
        return ResponseEntity.ok(toBalanceResponse(balance));
    }

    @GetMapping("/transactions")
    @Operation(
            summary = "List the authenticated user's transactions",
            description = "Returns newest transactions first.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Transactions returned",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = TransactionResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired JWT")
    })
    public ResponseEntity<List<TransactionResponse>> getTransactions() {
        User user = getAuthenticatedUser();
        List<TransactionResponse> transactions = walletService.getTransactions(user.getId())
                .stream()
                .map(this::toTransactionResponse)
                .toList();
        return ResponseEntity.ok(transactions);
    }

    private WalletBalanceResponse toBalanceResponse(WalletBalance balance) {
        return new WalletBalanceResponse(
                balance.getAsset(),
                balance.getAvailableAmount(),
                balance.getLockedAmount());
    }

    private TransactionResponse toTransactionResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getType(),
                transaction.getAsset(),
                transaction.getAmount(),
                transaction.getStatus(),
                transaction.getCreatedAt());
    }

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            throw new ForbiddenException("Authenticated user principal is unavailable");
        }
        return user;
    }
}
