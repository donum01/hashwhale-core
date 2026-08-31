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
    void authenticatedUserCannotViewOrMutateAnotherUsersWallet() throws Exception {
        User owner = saveUser("wallet-owner@example.com");
        User attacker = saveUser("wallet-attacker@example.com");
        WalletBalance ownerBalance = walletBalanceRepository.saveAndFlush(
                balance(owner, Asset.USDT, "500.00", "25.00"));
        String attackerToken = jwtService.generateToken(attacker);
        long initialTransactionCount = transactionRepository.count();

        assertForbidden(
                post("/api/wallet/{userId}/deposit", owner.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"asset":"USDT","amount":75.00}
                                """),
                attackerToken,
                "/api/wallet/" + owner.getId() + "/deposit");

        assertForbidden(
                post("/api/wallet/{userId}/withdraw", owner.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"asset":"USDT","amount":50.00}
                                """),
                attackerToken,
                "/api/wallet/" + owner.getId() + "/withdraw");

        assertForbidden(
                get("/api/wallet/{userId}/balances", owner.getId()),
                attackerToken,
                "/api/wallet/" + owner.getId() + "/balances");

        assertForbidden(
                get("/api/wallet/{userId}/transactions", owner.getId()),
                attackerToken,
                "/api/wallet/" + owner.getId() + "/transactions");

        WalletBalance unchangedBalance = walletBalanceRepository.findById(ownerBalance.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("500.00").compareTo(unchangedBalance.getAvailableAmount()));
        assertEquals(0, new BigDecimal("25.00").compareTo(unchangedBalance.getLockedAmount()));
        assertEquals(initialTransactionCount, transactionRepository.count());
    }

    @Test
    void authenticatedUserCannotViewBorrowOrRepayAnotherUsersLoans() throws Exception {
        User owner = saveUser("loan-owner@example.com");
        User attacker = saveUser("loan-attacker@example.com");
        WalletBalance ownerCollateral = walletBalanceRepository.saveAndFlush(
                balance(owner, Asset.BTC, "0.75", "0.25"));
        Loan ownerLoan = loanRepository.saveAndFlush(activeLoan(owner));
        String attackerToken = jwtService.generateToken(attacker);
        long initialLoanCount = loanRepository.count();
        long initialTransactionCount = transactionRepository.count();

        assertForbidden(
                post("/api/borrow/{userId}/loans", owner.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "collateralAsset":"BTC",
                                  "collateralAmount":0.10,
                                  "borrowedAmount":1000.00
                                }
                                """),
                attackerToken,
                "/api/borrow/" + owner.getId() + "/loans");

        assertForbidden(
                get("/api/borrow/{userId}/loans", owner.getId()),
                attackerToken,
                "/api/borrow/" + owner.getId() + "/loans");

        assertForbidden(
                post("/api/borrow/loans/{loanId}/repay", ownerLoan.getId()),
                attackerToken,
                "/api/borrow/loans/" + ownerLoan.getId() + "/repay");

        WalletBalance unchangedCollateral = walletBalanceRepository.findById(ownerCollateral.getId()).orElseThrow();
        Loan unchangedLoan = loanRepository.findById(ownerLoan.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("0.75").compareTo(unchangedCollateral.getAvailableAmount()));
        assertEquals(0, new BigDecimal("0.25").compareTo(unchangedCollateral.getLockedAmount()));
        assertEquals(LoanStatus.ACTIVE, unchangedLoan.getStatus());
        assertEquals(initialLoanCount, loanRepository.count());
        assertEquals(initialTransactionCount, transactionRepository.count());
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
