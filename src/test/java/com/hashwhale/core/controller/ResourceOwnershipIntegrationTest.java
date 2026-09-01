package com.hashwhale.core.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.Loan;
import com.hashwhale.core.entity.LoanStatus;
import com.hashwhale.core.entity.User;
import com.hashwhale.core.entity.WalletBalance;
import com.hashwhale.core.repository.LoanRepository;
import com.hashwhale.core.repository.TransactionRepository;
import com.hashwhale.core.repository.UserRepository;
import com.hashwhale.core.repository.WalletBalanceRepository;
import com.hashwhale.core.security.JwtService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class ResourceOwnershipIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletBalanceRepository walletBalanceRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    void walletEndpointsAreScopedToTheAuthenticatedUserWithoutASelectableUserId() throws Exception {
        User owner = saveUser("wallet-owner@example.com");
        User authenticatedUser = saveUser("wallet-authenticated@example.com");
        WalletBalance ownerBalance = walletBalanceRepository.saveAndFlush(
                balance(owner, Asset.USDT, "500.00", "25.00"));
        String token = jwtService.generateToken(authenticatedUser);
        long initialTransactionCount = transactionRepository.count();

        mockMvc.perform(post("/api/wallet/deposit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"asset":"USDT","amount":75.00}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableAmount").value(75.00));

        mockMvc.perform(post("/api/wallet/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"asset":"USDT","amount":50.00}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableAmount").value(25.00));

        mockMvc.perform(get("/api/wallet/balances")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].asset").value("USDT"))
                .andExpect(jsonPath("$[0].availableAmount").value(25.00))
                .andExpect(jsonPath("$[0].lockedAmount").value(0));

        mockMvc.perform(get("/api/wallet/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("WITHDRAW"))
                .andExpect(jsonPath("$[1].type").value("DEPOSIT"));

        WalletBalance unchangedBalance = walletBalanceRepository.findById(ownerBalance.getId()).orElseThrow();
        WalletBalance authenticatedBalance = walletBalanceRepository
                .findByUserIdAndAsset(authenticatedUser.getId(), Asset.USDT)
                .orElseThrow();
        assertEquals(0, new BigDecimal("500.00").compareTo(unchangedBalance.getAvailableAmount()));
        assertEquals(0, new BigDecimal("25.00").compareTo(unchangedBalance.getLockedAmount()));
        assertEquals(0, new BigDecimal("25.00").compareTo(authenticatedBalance.getAvailableAmount()));
        assertEquals(initialTransactionCount + 2, transactionRepository.count());
    }

    @Test
    void borrowEndpointsAreScopedToTheAuthenticatedUserAndRepaymentStillChecksOwnership()
            throws Exception {
        User owner = saveUser("loan-owner@example.com");
        User authenticatedUser = saveUser("loan-authenticated@example.com");
        WalletBalance ownerCollateral = walletBalanceRepository.saveAndFlush(
                balance(owner, Asset.BTC, "0.75", "0.25"));
        WalletBalance authenticatedCollateral = walletBalanceRepository.saveAndFlush(
                balance(authenticatedUser, Asset.BTC, "0.50", "0.00"));
        Loan ownerLoan = loanRepository.saveAndFlush(activeLoan(owner));
        String token = jwtService.generateToken(authenticatedUser);
        long initialLoanCount = loanRepository.count();
        long initialTransactionCount = transactionRepository.count();

        mockMvc.perform(post("/api/borrow/loans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "collateralAsset":"BTC",
                                  "collateralAmount":0.10,
                                  "borrowedAmount":1000.00
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/borrow/loans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].collateralAsset").value("BTC"))
                .andExpect(jsonPath("$[0].collateralAmount").value(0.10));

        assertForbidden(
                post("/api/borrow/loans/{loanId}/repay", ownerLoan.getId()),
                token,
                "/api/borrow/loans/" + ownerLoan.getId() + "/repay");

        WalletBalance unchangedCollateral = walletBalanceRepository.findById(ownerCollateral.getId()).orElseThrow();
        WalletBalance updatedAuthenticatedCollateral = walletBalanceRepository
                .findById(authenticatedCollateral.getId())
                .orElseThrow();
        Loan unchangedLoan = loanRepository.findById(ownerLoan.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("0.75").compareTo(unchangedCollateral.getAvailableAmount()));
        assertEquals(0, new BigDecimal("0.25").compareTo(unchangedCollateral.getLockedAmount()));
        assertEquals(
                0,
                new BigDecimal("0.40").compareTo(
                        updatedAuthenticatedCollateral.getAvailableAmount()));
        assertEquals(
                0,
                new BigDecimal("0.10").compareTo(
                        updatedAuthenticatedCollateral.getLockedAmount()));
        assertEquals(LoanStatus.ACTIVE, unchangedLoan.getStatus());
        assertEquals(1, loanRepository.findByUserId(authenticatedUser.getId()).size());
        assertEquals(initialLoanCount + 1, loanRepository.count());
        assertEquals(initialTransactionCount + 1, transactionRepository.count());
    }

    @Test
    void legacyUserIdRoutesAreNotMapped() throws Exception {
        User owner = saveUser("legacy-route-owner@example.com");
        User authenticatedUser = saveUser("legacy-route-authenticated@example.com");
        String token = jwtService.generateToken(authenticatedUser);

        mockMvc.perform(get("/api/wallet/{userId}/balances", owner.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/borrow/{userId}/loans", owner.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private void assertForbidden(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String token,
            String expectedPath) throws Exception {
        mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value(expectedPath));
    }

    private User saveUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("not-used-by-this-test");
        return userRepository.saveAndFlush(user);
    }

    private WalletBalance balance(
            User user, Asset asset, String availableAmount, String lockedAmount) {
        WalletBalance balance = new WalletBalance();
        balance.setUser(user);
        balance.setAsset(asset);
        balance.setAvailableAmount(new BigDecimal(availableAmount));
        balance.setLockedAmount(new BigDecimal(lockedAmount));
        return balance;
    }

    private Loan activeLoan(User user) {
        Loan loan = new Loan();
        loan.setUser(user);
        loan.setCollateralAsset(Asset.BTC);
        loan.setCollateralAmount(new BigDecimal("0.25"));
        loan.setBorrowedAmount(new BigDecimal("5000.00"));
        loan.setBorrowedAsset(Asset.USDT);
        loan.setInterestRateApr(BigDecimal.ZERO);
        loan.setStatus(LoanStatus.ACTIVE);
        return loan;
    }
}
