package com.hashwhale.core.demo;

import com.hashwhale.core.config.BorrowConfigurationProperties;
import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.CountryCode;
import com.hashwhale.core.entity.EarnPosition;
import com.hashwhale.core.entity.EarnPositionStatus;
import com.hashwhale.core.entity.EarnTermType;
import com.hashwhale.core.entity.KycStatus;
import com.hashwhale.core.entity.Loan;
import com.hashwhale.core.entity.LoanStatus;
import com.hashwhale.core.entity.Transaction;
import com.hashwhale.core.entity.TransactionStatus;
import com.hashwhale.core.entity.TransactionType;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates a deterministic, internally reconciled showcase account. */
@Service
@Profile("demo")
public class DemoDataService {

    private static final BigDecimal PERCENT_DENOMINATOR = new BigDecimal("100");
    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");
    private static final int MONEY_SCALE = 18;

    private final UserRepository userRepository;
    private final WalletBalanceRepository walletBalanceRepository;
    private final LoanRepository loanRepository;
    private final EarnPositionRepository earnPositionRepository;
    private final TransactionRepository transactionRepository;
    private final PasswordEncoder passwordEncoder;
    private final BorrowConfigurationProperties borrowConfiguration;
    private final Clock applicationClock;

    public DemoDataService(
            UserRepository userRepository,
            WalletBalanceRepository walletBalanceRepository,
            LoanRepository loanRepository,
            EarnPositionRepository earnPositionRepository,
            TransactionRepository transactionRepository,
            PasswordEncoder passwordEncoder,
            BorrowConfigurationProperties borrowConfiguration,
            Clock applicationClock) {
        this.userRepository = userRepository;
        this.walletBalanceRepository = walletBalanceRepository;
        this.loanRepository = loanRepository;
        this.earnPositionRepository = earnPositionRepository;
        this.transactionRepository = transactionRepository;
        this.passwordEncoder = passwordEncoder;
        this.borrowConfiguration = borrowConfiguration;
        this.applicationClock = applicationClock;
    }

    @Transactional
    public DemoSeedResult reset(String email, String rawPassword) {
        String normalizedEmail = validateAndNormalizeEmail(email);
        validatePassword(rawPassword);
        String passwordHash = passwordEncoder.encode(rawPassword);

        deleteUserOwnedData();

        Instant now = applicationClock.instant();
        LocalDate today = LocalDate.now(applicationClock);
        User user = saveDemoUser(normalizedEmail, passwordHash, now);

        Map<Asset, BigDecimal> available = zeroBalances();
        Map<Asset, BigDecimal> locked = zeroBalances();
        List<Transaction> transactions = new ArrayList<>();

        deposit(user, available, transactions, Asset.BTC, amount("0.18"), daysAgo(now, 120));
        deposit(user, available, transactions, Asset.ETH, amount("3.2"), daysAgo(now, 112));
        deposit(user, available, transactions, Asset.USDT, amount("9000"), daysAgo(now, 95));
        withdraw(user, available, transactions, Asset.BTC, amount("0.01"), daysAgo(now, 80));

        BigDecimal withdrawnEarnPrincipal = amount("0.02");
        moveToLocked(available, locked, Asset.BTC, withdrawnEarnPrincipal);
        transactions.add(transaction(
                user,
                TransactionType.EARN_SUBSCRIBE,
                Asset.BTC,
                withdrawnEarnPrincipal,
                daysAgo(now, 70)));

        BigDecimal repaidCollateral = amount("0.5");
        BigDecimal repaidPrincipal = amount("750");
        moveToLocked(available, locked, Asset.ETH, repaidCollateral);
        credit(available, Asset.USDT, repaidPrincipal);
        transactions.add(transaction(
                user, TransactionType.BORROW, Asset.USDT, repaidPrincipal, daysAgo(now, 55)));

        withdraw(user, available, transactions, Asset.USDT, amount("750"), daysAgo(now, 48));

        BigDecimal lockedUsdtPrincipal = amount("2500");
        moveToLocked(available, locked, Asset.USDT, lockedUsdtPrincipal);
        transactions.add(transaction(
                user,
                TransactionType.EARN_SUBSCRIBE,
                Asset.USDT,
                lockedUsdtPrincipal,
                daysAgo(now, 35)));

        BigDecimal withdrawnEarnRewards = calculateRewards(
                withdrawnEarnPrincipal, amount("1.50"), 40);
        BigDecimal withdrawnEarnCredit = withdrawnEarnPrincipal.add(withdrawnEarnRewards);
        unlock(locked, Asset.BTC, withdrawnEarnPrincipal);
        credit(available, Asset.BTC, withdrawnEarnCredit);
        transactions.add(transaction(
                user,
                TransactionType.EARN_WITHDRAW,
                Asset.BTC,
                withdrawnEarnCredit,
                daysAgo(now, 30)));

        BigDecimal flexibleEthPrincipal = amount("0.6");
        moveToLocked(available, locked, Asset.ETH, flexibleEthPrincipal);
        transactions.add(transaction(
                user,
                TransactionType.EARN_SUBSCRIBE,
                Asset.ETH,
                flexibleEthPrincipal,
                daysAgo(now, 21)));

        debit(available, Asset.USDT, repaidPrincipal);
        unlock(locked, Asset.ETH, repaidCollateral);
        credit(available, Asset.ETH, repaidCollateral);
        transactions.add(transaction(
                user, TransactionType.REPAY, Asset.USDT, repaidPrincipal, daysAgo(now, 18)));

        deposit(user, available, transactions, Asset.USDT, amount("1000"), daysAgo(now, 14));
        withdraw(user, available, transactions, Asset.ETH, amount("0.2"), daysAgo(now, 12));

        BigDecimal activeCollateral = amount("0.04");
        BigDecimal activePrincipal = amount("1600");
        moveToLocked(available, locked, Asset.BTC, activeCollateral);
        credit(available, Asset.USDT, activePrincipal);
        transactions.add(transaction(
                user, TransactionType.BORROW, Asset.USDT, activePrincipal, daysAgo(now, 9)));

        withdraw(user, available, transactions, Asset.USDT, amount("250"), daysAgo(now, 5));
        withdraw(user, available, transactions, Asset.ETH, amount("0.05"), daysAgo(now, 3));

        List<WalletBalance> balances = walletBalanceRepository.saveAll(walletBalances(user, available, locked));
        List<Loan> loans = loanRepository.saveAll(List.of(
                loan(
                        user,
                        Asset.ETH,
                        repaidCollateral,
                        repaidPrincipal,
                        LoanStatus.REPAID,
                        daysAgo(now, 55)),
                loan(
                        user,
                        Asset.BTC,
                        activeCollateral,
                        activePrincipal,
                        LoanStatus.ACTIVE,
                        daysAgo(now, 9))));
        List<EarnPosition> earnPositions = earnPositionRepository.saveAll(List.of(
                earnPosition(
                        user,
                        Asset.BTC,
                        withdrawnEarnPrincipal,
                        amount("1.50"),
                        EarnTermType.FLEXIBLE,
                        today.minusDays(70),
                        today.minusDays(30),
                        EarnPositionStatus.WITHDRAWN),
                earnPosition(
                        user,
                        Asset.USDT,
                        lockedUsdtPrincipal,
                        amount("6.00"),
                        EarnTermType.LOCKED_90,
                        today.minusDays(35),
                        today.plusDays(55),
                        EarnPositionStatus.ACTIVE),
                earnPosition(
                        user,
                        Asset.ETH,
                        flexibleEthPrincipal,
                        amount("2.25"),
                        EarnTermType.FLEXIBLE,
                        today.minusDays(21),
                        null,
                        EarnPositionStatus.ACTIVE)));
        List<Transaction> savedTransactions = transactionRepository.saveAll(transactions);

        return new DemoSeedResult(
                user.getId(),
                user.getEmail(),
                balances.size(),
                loans.size(),
                earnPositions.size(),
                savedTransactions.size());
    }

    private void deleteUserOwnedData() {
        transactionRepository.deleteAllInBatch();
        earnPositionRepository.deleteAllInBatch();
        loanRepository.deleteAllInBatch();
        walletBalanceRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    private User saveDemoUser(String email, String passwordHash, Instant now) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setCountryCode(CountryCode.SG);
        user.setKycStatus(KycStatus.VERIFIED);
        user.setCreatedAt(daysAgo(now, 150));
        return userRepository.save(user);
    }

    private List<WalletBalance> walletBalances(
            User user,
            Map<Asset, BigDecimal> available,
            Map<Asset, BigDecimal> locked) {
        List<WalletBalance> balances = new ArrayList<>();
        for (Asset asset : Asset.values()) {
            WalletBalance balance = new WalletBalance();
            balance.setUser(user);
            balance.setAsset(asset);
            balance.setAvailableAmount(available.get(asset));
            balance.setLockedAmount(locked.get(asset));
            balances.add(balance);
        }
        return balances;
    }

    private Loan loan(
            User user,
            Asset collateralAsset,
            BigDecimal collateralAmount,
            BigDecimal borrowedAmount,
            LoanStatus status,
            Instant createdAt) {
        Loan loan = new Loan();
        loan.setUser(user);
        loan.setCollateralAsset(collateralAsset);
        loan.setCollateralAmount(collateralAmount);
        loan.setBorrowedAmount(borrowedAmount);
        loan.setBorrowedAsset(Asset.USDT);
        loan.setInterestRateApr(borrowConfiguration.getInterestRateApr());
        loan.setStatus(status);
        loan.setCreatedAt(createdAt);
        return loan;
    }

    private EarnPosition earnPosition(
            User user,
            Asset asset,
            BigDecimal principal,
            BigDecimal apy,
            EarnTermType termType,
            LocalDate startDate,
            LocalDate endDate,
            EarnPositionStatus status) {
        EarnPosition position = new EarnPosition();
        position.setUser(user);
        position.setAsset(asset);
        position.setPrincipalAmount(principal);
        position.setApy(apy);
        position.setTermType(termType);
        position.setStartDate(startDate);
        position.setEndDate(endDate);
        position.setStatus(status);
        return position;
    }

    private void deposit(
            User user,
            Map<Asset, BigDecimal> available,
            List<Transaction> transactions,
            Asset asset,
            BigDecimal amount,
            Instant createdAt) {
        credit(available, asset, amount);
        transactions.add(transaction(user, TransactionType.DEPOSIT, asset, amount, createdAt));
    }

    private void withdraw(
            User user,
            Map<Asset, BigDecimal> available,
            List<Transaction> transactions,
            Asset asset,
            BigDecimal amount,
            Instant createdAt) {
        debit(available, asset, amount);
        transactions.add(transaction(user, TransactionType.WITHDRAW, asset, amount, createdAt));
    }

    private Transaction transaction(
            User user,
            TransactionType type,
            Asset asset,
            BigDecimal amount,
            Instant createdAt) {
        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setType(type);
        transaction.setAsset(asset);
        transaction.setAmount(amount);
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCreatedAt(createdAt);
        return transaction;
    }

    private Map<Asset, BigDecimal> zeroBalances() {
        Map<Asset, BigDecimal> balances = new EnumMap<>(Asset.class);
        for (Asset asset : Asset.values()) {
            balances.put(asset, BigDecimal.ZERO);
        }
        return balances;
    }

    private void moveToLocked(
            Map<Asset, BigDecimal> available,
            Map<Asset, BigDecimal> locked,
            Asset asset,
            BigDecimal amount) {
        debit(available, asset, amount);
        credit(locked, asset, amount);
    }

    private void credit(Map<Asset, BigDecimal> balances, Asset asset, BigDecimal amount) {
        balances.put(asset, balances.get(asset).add(amount));
    }

    private void debit(Map<Asset, BigDecimal> balances, Asset asset, BigDecimal amount) {
        BigDecimal updated = balances.get(asset).subtract(amount);
        if (updated.signum() < 0) {
            throw new IllegalStateException("Demo ledger would create a negative " + asset + " balance");
        }
        balances.put(asset, updated);
    }

    private void unlock(Map<Asset, BigDecimal> locked, Asset asset, BigDecimal amount) {
        debit(locked, asset, amount);
    }

    private BigDecimal calculateRewards(BigDecimal principal, BigDecimal apy, long days) {
        return principal
                .multiply(apy)
                .multiply(BigDecimal.valueOf(days))
                .divide(PERCENT_DENOMINATOR.multiply(DAYS_PER_YEAR), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private Instant daysAgo(Instant now, long days) {
        return now.minus(days, ChronoUnit.DAYS);
    }

    private String validateAndNormalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Demo user email is required");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        int atIndex = normalized.indexOf('@');
        if (normalized.length() > 320
                || atIndex <= 0
                || atIndex == normalized.length() - 1
                || normalized.indexOf('@', atIndex + 1) >= 0) {
            throw new IllegalArgumentException("Demo user email must be a valid email address");
        }
        return normalized;
    }

    private void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 8 || rawPassword.length() > 72) {
            throw new IllegalArgumentException("Demo user password must contain between 8 and 72 characters");
        }
    }
}
