package com.hashwhale.core.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hashwhale.core.dto.LoginRequest;
import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.CountryCode;
import com.hashwhale.core.entity.EarnPositionStatus;
import com.hashwhale.core.entity.ExchangeRatePoint;
import com.hashwhale.core.entity.FiatCurrency;
import com.hashwhale.core.entity.KycStatus;
import com.hashwhale.core.entity.LoanStatus;
import com.hashwhale.core.entity.MarketPricePoint;
import com.hashwhale.core.entity.PriceSource;
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
import com.hashwhale.core.service.AuthService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
    "app.demo-seed.enabled=true",
    "app.demo-seed.reset=true",
    "app.demo-seed.email=demo@hashwhale.com",
    "app.demo-seed.password=InterviewDemo123!",
    "app.pricing.collector-enabled=false"
})
@ActiveProfiles("demo")
class DemoDataSeederIntegrationTest {

    @Autowired private DemoDataService demoDataService;
    @Autowired private UserRepository userRepository;
    @Autowired private WalletBalanceRepository walletBalanceRepository;
    @Autowired private LoanRepository loanRepository;
    @Autowired private EarnPositionRepository earnPositionRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private MarketPricePointRepository marketPricePointRepository;
    @Autowired private ExchangeRatePointRepository exchangeRatePointRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuthService authService;

    @Test
    void resetCreatesReconciledLoginReadyDemoDataAndPreservesMarketTables() {
        assertDemoScenario("InterviewDemo123!");

        MarketPricePoint marketPoint = new MarketPricePoint();
        marketPoint.setAsset(Asset.BTC);
        marketPoint.setQuoteCurrency(FiatCurrency.USD);
        marketPoint.setTimestamp(Instant.parse("2000-01-01T00:00:00Z"));
        marketPoint.setPrice(new BigDecimal("25000"));
        marketPoint.setSource(PriceSource.STATIC);
        Long marketPointId = marketPricePointRepository.saveAndFlush(marketPoint).getId();

        ExchangeRatePoint exchangeRatePoint = new ExchangeRatePoint();
        exchangeRatePoint.setQuoteCurrency(FiatCurrency.SGD);
        exchangeRatePoint.setTimestamp(Instant.parse("2000-01-01T00:00:00Z"));
        exchangeRatePoint.setFiatPerUsd(new BigDecimal("55"));
        exchangeRatePoint.setSource(PriceSource.STATIC);
        Long exchangeRatePointId = exchangeRatePointRepository.saveAndFlush(exchangeRatePoint).getId();

        User extraUser = new User();
        extraUser.setEmail("remove-me@example.com");
        extraUser.setPasswordHash("not-a-real-password-hash");
        userRepository.saveAndFlush(extraUser);

        demoDataService.reset("DEMO@HASHWHALE.COM", "ReplacementDemo123!");

        assertTrue(marketPricePointRepository.existsById(marketPointId));
        assertTrue(exchangeRatePointRepository.existsById(exchangeRatePointId));
        assertFalse(userRepository.existsByEmail("remove-me@example.com"));
        assertDemoScenario("ReplacementDemo123!");
    }

    private void assertDemoScenario(String rawPassword) {
        assertEquals(1, userRepository.count());
        User user = userRepository.findByEmail("demo@hashwhale.com").orElseThrow();
        assertEquals(CountryCode.SG, user.getCountryCode());
        assertEquals(KycStatus.VERIFIED, user.getKycStatus());
        assertTrue(passwordEncoder.matches(rawPassword, user.getPasswordHash()));

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(user.getEmail());
        loginRequest.setPassword(rawPassword);
        assertFalse(authService.login(loginRequest).isBlank());

        assertEquals(3, walletBalanceRepository.findByUserId(user.getId()).size());
        assertBalance(user.getId(), Asset.BTC, "0.130032876712328767", "0.04");
        assertBalance(user.getId(), Asset.ETH, "2.35", "0.6");
        assertBalance(user.getId(), Asset.USDT, "8100", "2500");

        assertEquals(1, loanRepository.findByUserIdAndStatus(user.getId(), LoanStatus.ACTIVE).size());
        assertEquals(1, loanRepository.findByUserIdAndStatus(user.getId(), LoanStatus.REPAID).size());
        assertEquals(
                2,
                earnPositionRepository
                        .findByUserIdAndStatus(user.getId(), EarnPositionStatus.ACTIVE)
                        .size());
        assertEquals(
                1,
                earnPositionRepository
                        .findByUserIdAndStatus(user.getId(), EarnPositionStatus.WITHDRAWN)
                        .size());

        assertEquals(16, transactionRepository.findByUserIdOrderByCreatedAtDescIdDesc(user.getId()).size());
        assertEquals(
                TransactionType.WITHDRAW,
                transactionRepository
                        .findByUserIdOrderByCreatedAtDescIdDesc(user.getId())
                        .getFirst()
                        .getType());
    }

    private void assertBalance(Long userId, Asset asset, String available, String locked) {
        WalletBalance balance = walletBalanceRepository
                .findByUserIdAndAsset(userId, asset)
                .orElseThrow();
        assertEquals(0, new BigDecimal(available).compareTo(balance.getAvailableAmount()));
        assertEquals(0, new BigDecimal(locked).compareTo(balance.getLockedAmount()));
    }
}
