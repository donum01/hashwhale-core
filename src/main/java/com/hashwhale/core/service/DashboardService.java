package com.hashwhale.core.service;

import com.hashwhale.core.config.BorrowConfigurationProperties;
import com.hashwhale.core.dto.BorrowHealth;
import com.hashwhale.core.dto.DashboardAlertResponse;
import com.hashwhale.core.dto.DashboardAlertSeverity;
import com.hashwhale.core.dto.DashboardRecommendationResponse;
import com.hashwhale.core.dto.DashboardSummaryResponse;
import com.hashwhale.core.dto.TransactionResponse;
import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.EarnPosition;
import com.hashwhale.core.entity.EarnPositionStatus;
import com.hashwhale.core.entity.KycStatus;
import com.hashwhale.core.entity.Loan;
import com.hashwhale.core.entity.LoanStatus;
import com.hashwhale.core.entity.Transaction;
import com.hashwhale.core.entity.User;
import com.hashwhale.core.entity.WalletBalance;
import com.hashwhale.core.repository.EarnPositionRepository;
import com.hashwhale.core.repository.LoanRepository;
import com.hashwhale.core.repository.TransactionRepository;
import com.hashwhale.core.repository.UserRepository;
import com.hashwhale.core.repository.WalletBalanceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int SUMMARY_SCALE = 8;

    private final UserRepository userRepository;
    private final WalletBalanceRepository walletBalanceRepository;
    private final LoanRepository loanRepository;
    private final EarnPositionRepository earnPositionRepository;
    private final TransactionRepository transactionRepository;
    private final EarnService earnService;
    private final PriceService priceService;
    private final BorrowConfigurationProperties borrowConfiguration;
    private final Clock applicationClock;

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("User id must be greater than zero");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        List<WalletBalance> balances = walletBalanceRepository.findByUserId(userId);
        List<Loan> activeLoans = loanRepository.findByUserIdAndStatus(userId, LoanStatus.ACTIVE);
        List<EarnPosition> activeEarnPositions = earnPositionRepository
                .findByUserIdAndStatus(userId, EarnPositionStatus.ACTIVE);
        List<Transaction> recentTransactions = transactionRepository
                .findTop5ByUserIdOrderByCreatedAtDescIdDesc(userId);
        EarnSummary earnSummary = earnService.getSummary(userId);

        Map<Asset, BigDecimal> prices = currentPrices();
        BigDecimal walletAssetsUsd = BigDecimal.ZERO;
        BigDecimal availableUsd = BigDecimal.ZERO;
        Map<Asset, BigDecimal> availableByAsset = new EnumMap<>(Asset.class);
        for (WalletBalance balance : balances) {
            BigDecimal price = prices.get(balance.getAsset());
            walletAssetsUsd = walletAssetsUsd.add(
                    balance.getAvailableAmount().add(balance.getLockedAmount()).multiply(price));
            availableUsd = availableUsd.add(balance.getAvailableAmount().multiply(price));
            availableByAsset.put(balance.getAsset(), balance.getAvailableAmount());
        }

        BigDecimal totalDebtUsd = BigDecimal.ZERO;
        BigDecimal collateralUsd = BigDecimal.ZERO;
        BigDecimal highestLtv = BigDecimal.ZERO;
        for (Loan loan : activeLoans) {
            BigDecimal debtValue = loan.getBorrowedAmount().multiply(prices.get(loan.getBorrowedAsset()));
            BigDecimal collateralValue = loan.getCollateralAmount().multiply(prices.get(loan.getCollateralAsset()));
            totalDebtUsd = totalDebtUsd.add(debtValue);
            collateralUsd = collateralUsd.add(collateralValue);
            if (collateralValue.signum() > 0) {
                BigDecimal ltv = debtValue.multiply(ONE_HUNDRED)
                        .divide(collateralValue, SUMMARY_SCALE, RoundingMode.HALF_UP);
                if (ltv.compareTo(highestLtv) > 0) {
                    highestLtv = ltv;
                }
            }
        }

        BigDecimal totalAssetsUsd = walletAssetsUsd.add(earnSummary.accruedRewardsUsd());
        BigDecimal netAccountValueUsd = totalAssetsUsd.subtract(totalDebtUsd);
        BorrowHealth borrowHealth = borrowHealth(activeLoans.size(), highestLtv);
        PriceServiceStatus priceStatus = priceService.getStatus();
        LocalDate today = LocalDate.now(applicationClock);
        List<DashboardAlertResponse> alerts = alerts(
                user,
                highestLtv,
                borrowHealth,
                earnSummary.nextMaturityDate(),
                priceStatus,
                today);
        DashboardRecommendationResponse recommendation = recommendation(
                highestLtv,
                borrowHealth,
                earnSummary.nextMaturityDate(),
                availableByAsset,
                today);

        return new DashboardSummaryResponse(
                user.getEmail(),
                user.getKycStatus(),
                user.getPreferredFiatCurrency(),
                scaled(netAccountValueUsd),
                scaled(totalAssetsUsd),
                scaled(totalDebtUsd),
                scaled(availableUsd),
                scaled(earnSummary.totalPrincipalUsd()),
                scaled(collateralUsd),
                scaled(earnSummary.accruedRewardsUsd()),
                activeLoans.size(),
                scaled(highestLtv),
                borrowHealth,
                activeEarnPositions.size(),
                scaled(earnSummary.weightedAverageApy()),
                earnSummary.nextMaturityDate(),
                priceStatus.source(),
                priceStatus.updatedAt(),
                priceStatus.stale(),
                List.copyOf(alerts),
                recommendation,
                recentTransactions.stream().map(this::toTransactionResponse).toList());
    }

    private Map<Asset, BigDecimal> currentPrices() {
        Map<Asset, BigDecimal> prices = new EnumMap<>(Asset.class);
        for (Asset asset : Asset.values()) {
            prices.put(asset, priceService.getUsdPrice(asset));
        }
        return prices;
    }

    private BorrowHealth borrowHealth(int activeLoanCount, BigDecimal highestLtv) {
        if (activeLoanCount == 0) {
            return BorrowHealth.NONE;
        }
        if (highestLtv.compareTo(borrowConfiguration.getMaxLtvPercent()) >= 0) {
            return BorrowHealth.AT_RISK;
        }
        if (highestLtv.compareTo(borrowConfiguration.getWarningLtvPercent()) >= 0) {
            return BorrowHealth.WARNING;
        }
        return BorrowHealth.HEALTHY;
    }

    private List<DashboardAlertResponse> alerts(
            User user,
            BigDecimal highestLtv,
            BorrowHealth borrowHealth,
            LocalDate nextMaturity,
            PriceServiceStatus priceStatus,
            LocalDate today) {
        List<DashboardAlertResponse> alerts = new ArrayList<>();
        if (borrowHealth == BorrowHealth.AT_RISK || borrowHealth == BorrowHealth.WARNING) {
            DashboardAlertSeverity severity = borrowHealth == BorrowHealth.AT_RISK
                    ? DashboardAlertSeverity.CRITICAL
                    : DashboardAlertSeverity.WARNING;
            alerts.add(new DashboardAlertResponse(
                    severity,
                    "Loan LTV needs attention",
                    "Your highest active-loan LTV is " + highestLtv.setScale(2, RoundingMode.HALF_UP) + "%.",
                    "/borrow",
                    "Review loans"));
        }
        if (nextMaturity != null && !nextMaturity.isAfter(today.plusDays(7))) {
            long days = Math.max(0, ChronoUnit.DAYS.between(today, nextMaturity));
            alerts.add(new DashboardAlertResponse(
                    DashboardAlertSeverity.INFO,
                    days == 0 ? "Earn position matured" : "Earn maturity approaching",
                    days == 0
                            ? "A locked Earn position is ready to withdraw."
                            : "Your next locked Earn position matures in " + days + " days.",
                    "/earn",
                    "View positions"));
        }
        if (priceStatus.stale()) {
            alerts.add(new DashboardAlertResponse(
                    DashboardAlertSeverity.WARNING,
                    "Live prices unavailable",
                    "Portfolio and risk figures are using cached or configured fallback prices.",
                    null,
                    null));
        }
        if (user.getKycStatus() != KycStatus.VERIFIED) {
            alerts.add(new DashboardAlertResponse(
                    DashboardAlertSeverity.INFO,
                    "Identity verification incomplete",
                    "KYC is " + user.getKycStatus().name().toLowerCase().replace('_', ' ') + ".",
                    null,
                    null));
        }
        return alerts;
    }

    private DashboardRecommendationResponse recommendation(
            BigDecimal highestLtv,
            BorrowHealth borrowHealth,
            LocalDate nextMaturity,
            Map<Asset, BigDecimal> availableByAsset,
            LocalDate today) {
        if (borrowHealth == BorrowHealth.AT_RISK || borrowHealth == BorrowHealth.WARNING) {
            return new DashboardRecommendationResponse(
                    "Protect your collateral",
                    "Your highest LTV is " + highestLtv.setScale(2, RoundingMode.HALF_UP)
                            + "%. Review the position before market movement increases risk.",
                    "/borrow",
                    "Review Borrow");
        }
        if (nextMaturity != null && !nextMaturity.isAfter(today.plusDays(7))) {
            return new DashboardRecommendationResponse(
                    "A maturity is approaching",
                    "Review the position and decide whether to withdraw or start a new term.",
                    "/earn",
                    "Review Earn");
        }

        BigDecimal availableUsdt = availableByAsset.getOrDefault(Asset.USDT, BigDecimal.ZERO);
        EarnProduct flexibleUsdt = earnService.getProducts().stream()
                .filter(product -> product.asset() == Asset.USDT && product.termType().name().equals("FLEXIBLE"))
                .findFirst()
                .orElse(null);
        if (flexibleUsdt != null && availableUsdt.compareTo(flexibleUsdt.minimumAmount()) >= 0) {
            return new DashboardRecommendationResponse(
                    "Put an idle USDT balance to work",
                    "You have " + availableUsdt.stripTrailingZeros().toPlainString()
                            + " USDT available. Flexible Earn currently offers "
                            + flexibleUsdt.apy().stripTrailingZeros().toPlainString() + "% APY.",
                    "/earn",
                    "Explore Earn");
        }
        if (availableByAsset.values().stream().allMatch(amount -> amount.signum() == 0)) {
            return new DashboardRecommendationResponse(
                    "Explore the simulated ledger",
                    "Add a simulated deposit to try Wallet, Earn, and Borrow without moving real funds.",
                    "/wallet",
                    "Open Wallet");
        }
        return new DashboardRecommendationResponse(
                "Review available Earn products",
                "Compare flexible access with fixed terms before allocating an available balance.",
                "/earn",
                "Explore Earn");
    }

    private TransactionResponse toTransactionResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getType(),
                transaction.getAsset(),
                transaction.getAmount(),
                transaction.getStatus(),
                transaction.getCreatedAt());
    }

    private BigDecimal scaled(BigDecimal value) {
        return value.setScale(SUMMARY_SCALE, RoundingMode.HALF_UP);
    }
}
