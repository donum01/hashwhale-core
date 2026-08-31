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
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "Authenticated user does not own the requested wallet",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<List<WalletBalanceResponse>> getBalances(@PathVariable @Positive Long userId) {
        verifyUserAccess(userId);
        List<WalletBalanceResponse> balances = walletService.getBalances(userId)
                .stream()
                .map(this::toBalanceResponse)
                .toList();
        return ResponseEntity.ok(balances);
    }

    @PostMapping("/{userId}/deposit")
    @Operation(summary = "Deposit an asset into a user's wallet")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Deposit completed and balance updated",
                    content = @Content(schema = @Schema(implementation = WalletBalanceResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid user, asset, or amount",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired JWT"),
            @ApiResponse(responseCode = "403", description = "Authenticated user does not own this wallet")
    })
    public ResponseEntity<WalletBalanceResponse> deposit(
            @PathVariable @Positive Long userId,
            @Valid @RequestBody WalletTransactionRequest request) {
        verifyUserAccess(userId);
        WalletBalance balance = walletService.deposit(userId, request.getAsset(), request.getAmount());
        return ResponseEntity.ok(toBalanceResponse(balance));
    }

    @PostMapping("/{userId}/withdraw")
    @Operation(summary = "Withdraw an available asset from a user's wallet")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Withdrawal completed and balance updated",
                    content = @Content(schema = @Schema(implementation = WalletBalanceResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid input or insufficient available balance",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired JWT"),
            @ApiResponse(responseCode = "403", description = "Authenticated user does not own this wallet")
    })
    public ResponseEntity<WalletBalanceResponse> withdraw(
            @PathVariable @Positive Long userId,
            @Valid @RequestBody WalletTransactionRequest request) {
        verifyUserAccess(userId);
        WalletBalance balance = walletService.withdraw(userId, request.getAsset(), request.getAmount());
        return ResponseEntity.ok(toBalanceResponse(balance));
    }

    @GetMapping("/{userId}/transactions")
    @Operation(summary = "List a user's transactions", description = "Returns newest transactions first.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Transactions returned",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = TransactionResponse.class)))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid user id",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired JWT"),
            @ApiResponse(responseCode = "403", description = "Authenticated user does not own this wallet")
    })
    public ResponseEntity<List<TransactionResponse>> getTransactions(@PathVariable @Positive Long userId) {
        verifyUserAccess(userId);
        List<TransactionResponse> transactions = walletService.getTransactions(userId)
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

    private void verifyUserAccess(Long requestedUserId) {
        User authenticatedUser = getAuthenticatedUser();
        if (!authenticatedUser.getId().equals(requestedUserId)) {
            throw new ForbiddenException(
                    "You are not authorized to access wallet resources for user " + requestedUserId);
        }
    }

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            throw new ForbiddenException("Authenticated user principal is unavailable");
        }
        return user;
    }
}
