package com.hashwhale.core.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.EarnPosition;
import com.hashwhale.core.entity.EarnPositionStatus;
import com.hashwhale.core.entity.EarnTermType;
import com.hashwhale.core.entity.Loan;
import com.hashwhale.core.entity.LoanStatus;
import com.hashwhale.core.entity.Transaction;
import com.hashwhale.core.entity.TransactionStatus;
import com.hashwhale.core.entity.TransactionType;
import com.hashwhale.core.entity.User;
import com.hashwhale.core.repository.EarnPositionRepository;
import com.hashwhale.core.repository.LoanRepository;
import com.hashwhale.core.repository.TransactionRepository;
import com.hashwhale.core.repository.UserRepository;
import com.hashwhale.core.security.JwtService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class HistoryPaginationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private EarnPositionRepository earnPositionRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    void walletTransactionsUseFilteredCursorBatches() throws Exception {
        User user = savedUser("wallet-history@example.com");
        for (int index = 0; index < 12; index++) {
            transactionRepository.save(transaction(user, TransactionType.DEPOSIT));
        }
        transactionRepository.save(transaction(user, TransactionType.WITHDRAW));
        transactionRepository.flush();

        String token = jwtService.generateToken(user);
        MvcResult firstPage = mockMvc.perform(get("/api/wallet/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("limit", "10")
                        .param("type", "DEPOSIT"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Has-More", "true"))
                .andExpect(header().exists("X-Next-Cursor"))
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].type").value("DEPOSIT"))
                .andReturn();

        String cursor = firstPage.getResponse().getHeader("X-Next-Cursor");
        assertNotNull(cursor);
        mockMvc.perform(get("/api/wallet/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("limit", "10")
                        .param("beforeId", cursor)
                        .param("type", "DEPOSIT"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Has-More", "false"))
                .andExpect(header().doesNotExist("X-Next-Cursor"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void closedLoansUseFilteredCursorBatches() throws Exception {
        User user = savedUser("loan-history@example.com");
        for (int index = 0; index < 12; index++) {
            loanRepository.save(loan(user, index % 2 == 0 ? LoanStatus.REPAID : LoanStatus.LIQUIDATED));
        }
        loanRepository.save(loan(user, LoanStatus.ACTIVE));
        loanRepository.flush();

        String token = jwtService.generateToken(user);
        MvcResult firstPage = mockMvc.perform(get("/api/borrow/loans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("limit", "10")
                        .param("status", "REPAID", "LIQUIDATED"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Has-More", "true"))
                .andExpect(jsonPath("$.length()").value(10))
                .andReturn();

        String cursor = firstPage.getResponse().getHeader("X-Next-Cursor");
        assertNotNull(cursor);
        mockMvc.perform(get("/api/borrow/loans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("limit", "10")
                        .param("beforeId", cursor)
                        .param("status", "REPAID", "LIQUIDATED"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Has-More", "false"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void earnHistoryUsesFilteredCursorBatches() throws Exception {
        User user = savedUser("earn-history@example.com");
        for (int index = 0; index < 12; index++) {
            earnPositionRepository.save(earnPosition(user, EarnPositionStatus.WITHDRAWN, index));
        }
        earnPositionRepository.save(earnPosition(user, EarnPositionStatus.ACTIVE, 12));
        earnPositionRepository.flush();

        String token = jwtService.generateToken(user);
        MvcResult firstPage = mockMvc.perform(get("/api/earn/positions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("limit", "10")
                        .param("status", "WITHDRAWN"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Has-More", "true"))
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].status").value("WITHDRAWN"))
                .andReturn();

        String cursor = firstPage.getResponse().getHeader("X-Next-Cursor");
        assertNotNull(cursor);
        mockMvc.perform(get("/api/earn/positions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("limit", "10")
                        .param("beforeId", cursor)
                        .param("status", "WITHDRAWN"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Has-More", "false"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    private User savedUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("not-used-by-this-test");
        return userRepository.saveAndFlush(user);
    }

    private Transaction transaction(User user, TransactionType type) {
        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setType(type);
        transaction.setAsset(Asset.USDT);
        transaction.setAmount(BigDecimal.ONE);
        transaction.setStatus(TransactionStatus.COMPLETED);
        return transaction;
    }

    private Loan loan(User user, LoanStatus status) {
        Loan loan = new Loan();
        loan.setUser(user);
        loan.setCollateralAsset(Asset.BTC);
        loan.setCollateralAmount(new BigDecimal("0.1"));
        loan.setBorrowedAmount(new BigDecimal("1000"));
        loan.setBorrowedAsset(Asset.USDT);
        loan.setInterestRateApr(new BigDecimal("5"));
        loan.setStatus(status);
        return loan;
    }

    private EarnPosition earnPosition(User user, EarnPositionStatus status, int ageInDays) {
        LocalDate startDate = LocalDate.now().minusDays(ageInDays + 1L);
        EarnPosition position = new EarnPosition();
        position.setUser(user);
        position.setAsset(Asset.USDT);
        position.setPrincipalAmount(new BigDecimal("100"));
        position.setApy(new BigDecimal("4.5"));
        position.setTermType(EarnTermType.FLEXIBLE);
        position.setStartDate(startDate);
        position.setEndDate(status == EarnPositionStatus.WITHDRAWN ? startDate.plusDays(1) : null);
        position.setStatus(status);
        return position;
    }
}
