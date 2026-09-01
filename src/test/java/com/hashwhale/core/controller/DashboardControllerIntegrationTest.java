package com.hashwhale.core.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.CountryCode;
import com.hashwhale.core.entity.EarnPosition;
import com.hashwhale.core.entity.EarnPositionStatus;
import com.hashwhale.core.entity.EarnTermType;
import com.hashwhale.core.entity.ExchangeRatePoint;
import com.hashwhale.core.entity.FiatCurrency;
import com.hashwhale.core.entity.KycStatus;
import com.hashwhale.core.entity.Loan;
import com.hashwhale.core.entity.LoanStatus;
import com.hashwhale.core.entity.MarketPricePoint;
import com.hashwhale.core.entity.PriceSource;
import com.hashwhale.core.entity.Transaction;
import com.hashwhale.core.entity.TransactionStatus;
import com.hashwhale.core.entity.TransactionType;
import com.hashwhale.core.entity.User;
import com.hashwhale.core.entity.WalletBalance;
import com.hashwhale.core.repository.EarnPositionRepository;
import com.hashwhale.core.repository.ExchangeRatePointRepository;
import com.hashwhale.core.repository.LoanRepository;
import com.hashwhale.core.repository.MarketPricePointRepository;
import com.hashwhale.core.repository.TransactionRepository;
import com.hashwhale.core.repository.UserRepository;
import com.hashwhale.core.repository.WalletBalanceRepository;
import com.hashwhale.core.security.JwtService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private WalletBalanceRepository walletBalanceRepository;
    @Autowired private LoanRepository loanRepository;
    @Autowired private EarnPositionRepository earnPositionRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private MarketPricePointRepository marketPricePointRepository;
    @Autowired private ExchangeRatePointRepository exchangeRatePointRepository;
    @Autowired private JwtService jwtService;

    @Test
    void dashboardAndMarketEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/market/prices/BTC/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    @Rollback
    void dashboardAggregatesProductsAndUsdtHistoryUsesUsersCountryCurrency() throws Exception {
        User user = savedUser();
        walletBalanceRepository.save(balance(user, Asset.BTC, "0.5", "0.1"));
        walletBalanceRepository.save(balance(user, Asset.USDT, "1000", "200"));
        loanRepository.save(activeLoan(user));
        earnPositionRepository.save(activeEarnPosition(user));
        transactionRepository.save(transaction(user));
        seedStoredMarketData();
        String authorization = "Bearer " + jwtService.generateToken(user);

        mockMvc.perform(get("/api/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.preferredFiatCurrency").value("PHP"))
                .andExpect(jsonPath("$.availableUsd").value(31000.0))
                .andExpect(jsonPath("$.earnPrincipalUsd").value(200.0))
                .andExpect(jsonPath("$.collateralUsd").value(6000.0))
                .andExpect(jsonPath("$.totalDebtUsd").value(1000.0))
                .andExpect(jsonPath("$.activeLoanCount").value(1))
                .andExpect(jsonPath("$.borrowHealth").value("HEALTHY"))
                .andExpect(jsonPath("$.activeEarnPositionCount").value(1))
                .andExpect(jsonPath("$.recentTransactions.length()").value(1));

        mockMvc.perform(get("/api/market/prices/USDT/history")
                        .queryParam("range", "1D")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asset").value("USDT"))
                .andExpect(jsonPath("$.quoteCurrency").value("PHP"))
                .andExpect(jsonPath("$.currentPrice").value(58.0))
                .andExpect(jsonPath("$.source").value("STATIC"))
                .andExpect(jsonPath("$.points.length()").value(2));

        mockMvc.perform(get("/api/market/prices/BTC/history")
                        .queryParam("range", "7D")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quoteCurrency").value("USD"))
                .andExpect(jsonPath("$.currentPrice").value(60000.0));
    }

    @Test
    @Transactional
    @Rollback
    void invalidHistoryRangeReturnsBadRequest() throws Exception {
        User user = savedUser();

        mockMvc.perform(get("/api/market/prices/ETH/history")
                        .queryParam("range", "1Y")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Unsupported price range. Use 1D, 7D, 30D, or 90D"));
    }

    private User savedUser() {
        User user = new User();
        user.setEmail("dashboard-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("encoded-password");
        user.setCountryCode(CountryCode.PH);
        user.setKycStatus(KycStatus.VERIFIED);
        return userRepository.saveAndFlush(user);
    }

    private WalletBalance balance(
            User user, Asset asset, String available, String locked) {
        WalletBalance balance = new WalletBalance();
        balance.setUser(user);
        balance.setAsset(asset);
        balance.setAvailableAmount(new BigDecimal(available));
        balance.setLockedAmount(new BigDecimal(locked));
        return balance;
    }

    private Loan activeLoan(User user) {
        Loan loan = new Loan();
        loan.setUser(user);
        loan.setCollateralAsset(Asset.BTC);
        loan.setCollateralAmount(new BigDecimal("0.1"));
        loan.setBorrowedAsset(Asset.USDT);
        loan.setBorrowedAmount(new BigDecimal("1000"));
        loan.setInterestRateApr(new BigDecimal("2.88"));
        loan.setStatus(LoanStatus.ACTIVE);
        return loan;
    }

    private EarnPosition activeEarnPosition(User user) {
        EarnPosition position = new EarnPosition();
        position.setUser(user);
        position.setAsset(Asset.USDT);
        position.setPrincipalAmount(new BigDecimal("200"));
        position.setApy(new BigDecimal("4.5"));
        position.setTermType(EarnTermType.FLEXIBLE);
        position.setStartDate(LocalDate.now().minusDays(10));
        position.setStatus(EarnPositionStatus.ACTIVE);
        return position;
    }

    private Transaction transaction(User user) {
        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setType(TransactionType.DEPOSIT);
        transaction.setAsset(Asset.USDT);
        transaction.setAmount(new BigDecimal("1200"));
        transaction.setStatus(TransactionStatus.COMPLETED);
        return transaction;
    }

    private void seedStoredMarketData() {
        Instant now = Instant.now();
        marketPricePointRepository.saveAll(List.of(
                marketPoint(Asset.BTC, now.minusSeconds(6 * 86400), "59000"),
                marketPoint(Asset.BTC, now.minusSeconds(60), "60000"),
                marketPoint(Asset.USDT, now.minusSeconds(12 * 3600), "0.999"),
                marketPoint(Asset.USDT, now.minusSeconds(60), "1.000")));
        exchangeRatePointRepository.saveAll(List.of(
                exchangeRate(now.minusSeconds(12 * 3600), "57"),
                exchangeRate(now, "58")));
    }

    private MarketPricePoint marketPoint(Asset asset, Instant timestamp, String price) {
        MarketPricePoint point = new MarketPricePoint();
        point.setAsset(asset);
        point.setQuoteCurrency(FiatCurrency.USD);
        point.setTimestamp(timestamp);
        point.setPrice(new BigDecimal(price));
        point.setSource(PriceSource.STATIC);
        return point;
    }

    private ExchangeRatePoint exchangeRate(Instant timestamp, String fiatPerUsd) {
        ExchangeRatePoint point = new ExchangeRatePoint();
        point.setQuoteCurrency(FiatCurrency.PHP);
        point.setTimestamp(timestamp);
        point.setFiatPerUsd(new BigDecimal(fiatPerUsd));
        point.setSource(PriceSource.STATIC);
        return point;
    }
}
