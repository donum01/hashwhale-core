package com.hashwhale.core.controller;

import com.hashwhale.core.dto.ApiErrorResponse;
import com.hashwhale.core.dto.CreateLoanRequest;
import com.hashwhale.core.dto.LoanResponse;
import com.hashwhale.core.entity.Loan;
import com.hashwhale.core.service.BorrowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/borrow")
@Tag(name = "Borrow", description = "Create, repay, and view collateralized loans")
public class BorrowController {

    private final BorrowService borrowService;

    @PostMapping("/{userId}/loans")
    @Operation(summary = "Create a loan", description = "Creates a USDT loan secured by the supplied collateral.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Loan created",
                    content = @Content(schema = @Schema(implementation = LoanResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid input, insufficient collateral, or LTV limit exceeded",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<LoanResponse> createLoan(
            @PathVariable @Positive Long userId,
            @Valid @RequestBody CreateLoanRequest request) {
        Loan loan = borrowService.createLoan(
                userId,
                request.getCollateralAsset(),
                request.getCollateralAmount(),
                request.getBorrowedAmount());
        URI location = URI.create("/api/borrow/" + userId + "/loans/" + loan.getId());
        return ResponseEntity.created(location).body(toResponse(loan));
    }

    @PostMapping("/loans/{loanId}/repay")
    @Operation(summary = "Repay a loan", description = "Marks an active loan as repaid and unlocks its collateral.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Loan repaid",
                    content = @Content(schema = @Schema(implementation = LoanResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid loan id or loan not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "409",
                    description = "Loan cannot be repaid in its current state",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<LoanResponse> repayLoan(@PathVariable @Positive Long loanId) {
        return ResponseEntity.ok(toResponse(borrowService.repayLoan(loanId)));
    }

    @GetMapping("/{userId}/loans")
    @Operation(summary = "List a user's loans")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Loans returned",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = LoanResponse.class)))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid user id or user not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<List<LoanResponse>> getLoansForUser(@PathVariable @Positive Long userId) {
        List<LoanResponse> loans = borrowService.getLoansForUser(userId)
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(loans);
    }

    private LoanResponse toResponse(Loan loan) {
        return new LoanResponse(
                loan.getId(),
                loan.getCollateralAsset(),
                loan.getCollateralAmount(),
                loan.getBorrowedAmount(),
                loan.getBorrowedAsset(),
                loan.getInterestRateApr(),
                loan.getStatus(),
                loan.getCreatedAt());
    }
}
