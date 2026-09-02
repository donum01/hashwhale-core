package com.hashwhale.core.controller;

import com.hashwhale.core.dto.ApiErrorResponse;
import com.hashwhale.core.dto.CreateLoanRequest;
import com.hashwhale.core.dto.LoanResponse;
import com.hashwhale.core.entity.Loan;
import com.hashwhale.core.entity.LoanStatus;
import com.hashwhale.core.entity.User;
import com.hashwhale.core.service.BorrowService;
import com.hashwhale.core.service.ForbiddenException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/borrow")
@Tag(name = "Borrow", description = "Create, repay, and view collateralized loans")
public class BorrowController {

    private final BorrowService borrowService;

    @PostMapping("/loans")
    @Operation(
            summary = "Create a loan for the authenticated user",
            description = "Creates a USDT loan secured by the supplied collateral.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Loan created",
                    content = @Content(schema = @Schema(implementation = LoanResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid input, insufficient collateral, or LTV limit exceeded",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired JWT")
    })
    public ResponseEntity<LoanResponse> createLoan(
            @Valid @RequestBody CreateLoanRequest request) {
        User user = getAuthenticatedUser();
        Loan loan = borrowService.createLoan(
                user.getId(),
                request.getCollateralAsset(),
                request.getCollateralAmount(),
                request.getBorrowedAmount());
        URI location = URI.create("/api/borrow/loans/" + loan.getId());
        return ResponseEntity.created(location).body(toResponse(loan));
    }

    @PostMapping("/loans/{loanId}/repay")
    @Operation(
            summary = "Repay a loan",
            description = "Deducts the borrowed principal, marks the loan as repaid, and unlocks its collateral.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Loan repaid",
                    content = @Content(schema = @Schema(implementation = LoanResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid loan, loan not found, or insufficient repayment balance",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "409",
                    description = "Loan cannot be repaid in its current state",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "Authenticated user does not own the loan",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<LoanResponse> repayLoan(@PathVariable @Positive Long loanId) {
        User authenticatedUser = getAuthenticatedUser();
        return ResponseEntity.ok(toResponse(borrowService.repayLoan(loanId, authenticatedUser.getId())));
    }

    @GetMapping("/loans")
    @Operation(summary = "List the authenticated user's loans")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Loans returned",
                    headers = {
                            @Header(
                                    name = "X-Has-More",
                                    description = "Whether another batch is available",
                                    schema = @Schema(type = "boolean")),
                            @Header(
                                    name = "X-Next-Cursor",
                                    description = "Pass this value as beforeId to load the next batch",
                                    schema = @Schema(type = "integer", format = "int64"))
                    },
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = LoanResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired JWT")
    })
    public ResponseEntity<List<LoanResponse>> getLoansForUser(
            @Parameter(description = "Maximum records to return", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit,
            @Parameter(description = "Cursor returned by the previous response", example = "125")
            @RequestParam(required = false) @Positive Long beforeId,
            @Parameter(description = "Optional repeatable loan-status filter")
            @RequestParam(name = "status", required = false) Set<LoanStatus> statuses) {
        User user = getAuthenticatedUser();
        Slice<Loan> loanSlice = borrowService.getLoansForUser(
                user.getId(), statuses, beforeId, limit);
        List<LoanResponse> loans = loanSlice.getContent()
                .stream()
                .map(this::toResponse)
                .toList();
        return historyResponse(loanSlice, loans);
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

    private ResponseEntity<List<LoanResponse>> historyResponse(
            Slice<Loan> slice,
            List<LoanResponse> body) {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header("X-Has-More", Boolean.toString(slice.hasNext()));
        if (slice.hasNext() && !slice.getContent().isEmpty()) {
            Loan last = slice.getContent().get(slice.getContent().size() - 1);
            response.header("X-Next-Cursor", last.getId().toString());
        }
        return response.body(body);
    }

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            throw new ForbiddenException("Authenticated user principal is unavailable");
        }
        return user;
    }
}
