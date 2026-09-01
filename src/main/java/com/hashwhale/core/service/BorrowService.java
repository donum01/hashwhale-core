package com.hashwhale.core.service;

import com.hashwhale.core.config.BorrowConfigurationProperties;
import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.Loan;
import com.hashwhale.core.entity.LoanStatus;
import com.hashwhale.core.entity.Transaction;
import com.hashwhale.core.entity.TransactionStatus;
import com.hashwhale.core.entity.TransactionType;
import com.hashwhale.core.entity.User;
import com.hashwhale.core.entity.WalletBalance;
import com.hashwhale.core.repository.LoanRepository;
import com.hashwhale.core.repository.TransactionRepository;
import com.hashwhale.core.repository.UserRepository;
import com.hashwhale.core.repository.WalletBalanceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BorrowService {

    private static final Asset DEFAULT_BORROWED_ASSET = Asset.USDT;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int LTV_SCALE = 8;

    private final UserRepository userRepository;
    private final WalletBalanceRepository walletBalanceRepository;
    private final LoanRepository loanRepository;
    private final TransactionRepository transactionRepository;
    private final PriceService priceService;
    private final BorrowConfigurationProperties configuration;

    /**
     * Creates a USDT-denominated loan secured by an amount of the supplied collateral asset.
     */
    @Transactional
    public Loan createLoan(
            Long userId,
            Asset collateralAsset,
            BigDecimal collateralAmount,
            BigDecimal borrowedAmount) {
        validateRequired(userId, "User id");
        validateRequired(collateralAsset, "Collateral asset");
        validateCollateralAsset(collateralAsset);
        validatePositive(collateralAmount, "Collateral amount");
        validatePositive(borrowedAmount, "Borrowed amount");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        WalletBalance collateralBalance = walletBalanceRepository
                .findByUserIdAndAssetForUpdate(userId, collateralAsset)
                .orElseThrow(() -> new InsufficientBalanceException(
                        "No wallet balance exists for collateral asset " + collateralAsset));

        if (collateralBalance.getAvailableAmount().compareTo(collateralAmount) < 0) {
            throw new InsufficientBalanceException("Insufficient available collateral balance");
        }

        Loan loan = new Loan();
        loan.setUser(user);
        loan.setCollateralAsset(collateralAsset);
        loan.setCollateralAmount(collateralAmount);
        loan.setBorrowedAmount(borrowedAmount);
        loan.setBorrowedAsset(DEFAULT_BORROWED_ASSET);
        loan.setInterestRateApr(configuration.getInterestRateApr());
        loan.setStatus(LoanStatus.ACTIVE);

        BigDecimal currentCollateralPrice = priceService.getUsdPrice(collateralAsset);
        BigDecimal ltv = calculateLtv(loan, currentCollateralPrice);
        if (ltv.compareTo(configuration.getMaxLtvPercent()) > 0) {
            throw new LtvLimitExceededException(
                    "Loan-to-value of " + ltv + "% exceeds the "
                            + configuration.getMaxLtvPercent() + "% limit");
        }

        WalletBalance borrowedAssetBalance = findOrCreateBorrowedAssetBalance(user, collateralBalance);

        collateralBalance.setAvailableAmount(
                collateralBalance.getAvailableAmount().subtract(collateralAmount));
        collateralBalance.setLockedAmount(collateralBalance.getLockedAmount().add(collateralAmount));
        borrowedAssetBalance.setAvailableAmount(
                borrowedAssetBalance.getAvailableAmount().add(borrowedAmount));
        saveAffectedBalances(collateralBalance, borrowedAssetBalance);

        Loan savedLoan = loanRepository.save(loan);
        transactionRepository.save(createTransaction(
                user,
                TransactionType.BORROW,
                DEFAULT_BORROWED_ASSET,
                borrowedAmount));

        return savedLoan;
    }

    /**
     * Deducts the borrowed principal, records repayment, and releases the loan's collateral.
     */
    @Transactional
    public Loan repayLoan(Long loanId) {
        return repayLoanInternal(loanId, null);
    }

    @Transactional
    public Loan repayLoan(Long loanId, Long authenticatedUserId) {
        validateRequired(authenticatedUserId, "Authenticated user id");
        return repayLoanInternal(loanId, authenticatedUserId);
    }

    private Loan repayLoanInternal(Long loanId, Long authenticatedUserId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));
        if (authenticatedUserId != null && !loan.getUser().getId().equals(authenticatedUserId)) {
            throw new ForbiddenException("You are not authorized to repay this loan");
        }
        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new IllegalStateException("Only active loans can be repaid");
        }

        WalletBalance collateralBalance = walletBalanceRepository
                .findByUserIdAndAssetForUpdate(loan.getUser().getId(), loan.getCollateralAsset())
                .orElseThrow(() -> new IllegalStateException("Collateral wallet balance does not exist"));
        if (collateralBalance.getLockedAmount().compareTo(loan.getCollateralAmount()) < 0) {
            throw new IllegalStateException("Locked collateral is less than the loan collateral amount");
        }

        WalletBalance repaymentBalance = findRepaymentBalance(loan, collateralBalance);
        if (repaymentBalance.getAvailableAmount().compareTo(loan.getBorrowedAmount()) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient available " + loan.getBorrowedAsset() + " balance to repay loan");
        }

        repaymentBalance.setAvailableAmount(
                repaymentBalance.getAvailableAmount().subtract(loan.getBorrowedAmount()));
        collateralBalance.setLockedAmount(
                collateralBalance.getLockedAmount().subtract(loan.getCollateralAmount()));
        collateralBalance.setAvailableAmount(
                collateralBalance.getAvailableAmount().add(loan.getCollateralAmount()));
        loan.setStatus(LoanStatus.REPAID);

        saveAffectedBalances(collateralBalance, repaymentBalance);
        Loan savedLoan = loanRepository.save(loan);
        transactionRepository.save(createTransaction(
                loan.getUser(),
                TransactionType.REPAY,
                loan.getBorrowedAsset(),
                loan.getBorrowedAmount()));

        return savedLoan;
    }

    @Transactional(readOnly = true)
    public List<Loan> getLoansForUser(Long userId) {
        validateRequired(userId, "User id");
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        return loanRepository.findByUserId(userId);
    }

    /**
     * Returns LTV as a percentage: borrowed USD value / collateral USD value * 100.
     */
    public BigDecimal calculateLtv(Loan loan, BigDecimal currentCollateralPrice) {
        validateRequired(loan, "Loan");
        validateRequired(loan.getBorrowedAsset(), "Borrowed asset");
        validatePositive(currentCollateralPrice, "Current collateral price");
        validatePositive(loan.getCollateralAmount(), "Collateral amount");
        validatePositive(loan.getBorrowedAmount(), "Borrowed amount");

        BigDecimal borrowedAssetPrice = priceService.getUsdPrice(loan.getBorrowedAsset());
        validatePositive(borrowedAssetPrice, "Borrowed asset price");

        BigDecimal collateralUsdValue = loan.getCollateralAmount().multiply(currentCollateralPrice);
        BigDecimal borrowedUsdValue = loan.getBorrowedAmount().multiply(borrowedAssetPrice);

        return borrowedUsdValue
                .multiply(ONE_HUNDRED)
                .divide(collateralUsdValue, LTV_SCALE, RoundingMode.HALF_UP);
    }

    private WalletBalance findOrCreateBorrowedAssetBalance(
            User user, WalletBalance collateralBalance) {
        if (collateralBalance.getAsset() == DEFAULT_BORROWED_ASSET) {
            return collateralBalance;
        }

        return walletBalanceRepository
                .findByUserIdAndAssetForUpdate(user.getId(), DEFAULT_BORROWED_ASSET)
                .orElseGet(() -> newBalance(user, DEFAULT_BORROWED_ASSET));
    }

    private WalletBalance findRepaymentBalance(Loan loan, WalletBalance collateralBalance) {
        if (collateralBalance.getAsset() == loan.getBorrowedAsset()) {
            return collateralBalance;
        }

        return walletBalanceRepository
                .findByUserIdAndAssetForUpdate(loan.getUser().getId(), loan.getBorrowedAsset())
                .orElseThrow(() -> new InsufficientBalanceException(
                        "No wallet balance exists for repayment asset " + loan.getBorrowedAsset()));
    }

    private WalletBalance newBalance(User user, Asset asset) {
        WalletBalance balance = new WalletBalance();
        balance.setUser(user);
        balance.setAsset(asset);
        balance.setAvailableAmount(BigDecimal.ZERO);
        balance.setLockedAmount(BigDecimal.ZERO);
        return balance;
    }

    private void saveAffectedBalances(
            WalletBalance collateralBalance, WalletBalance borrowedAssetBalance) {
        walletBalanceRepository.save(collateralBalance);
        if (borrowedAssetBalance != collateralBalance) {
            walletBalanceRepository.save(borrowedAssetBalance);
        }
    }

    private Transaction createTransaction(User user, TransactionType type, Asset asset, BigDecimal amount) {
        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setType(type);
        transaction.setAsset(asset);
        transaction.setAmount(amount);
        transaction.setStatus(TransactionStatus.COMPLETED);
        return transaction;
    }

    private void validatePositive(BigDecimal value, String fieldName) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero");
        }
    }

    private void validateCollateralAsset(Asset collateralAsset) {
        if (collateralAsset != Asset.BTC && collateralAsset != Asset.ETH) {
            throw new IllegalArgumentException("Collateral asset must be BTC or ETH");
        }
    }

    private void validateRequired(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
