package com.hashwhale.core.service;

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
    private static final BigDecimal DEFAULT_INTEREST_RATE_APR = BigDecimal.ZERO;
    private static final BigDecimal MAX_LTV_PERCENT = new BigDecimal("70");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int LTV_SCALE = 8;

    private final UserRepository userRepository;
    private final WalletBalanceRepository walletBalanceRepository;
    private final LoanRepository loanRepository;
    private final TransactionRepository transactionRepository;
    private final PriceService priceService;

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
        loan.setInterestRateApr(DEFAULT_INTEREST_RATE_APR);
        loan.setStatus(LoanStatus.ACTIVE);

        BigDecimal currentCollateralPrice = priceService.getUsdPrice(collateralAsset);
        BigDecimal ltv = calculateLtv(loan, currentCollateralPrice);
        if (ltv.compareTo(MAX_LTV_PERCENT) > 0) {
            throw new LtvLimitExceededException(
                    "Loan-to-value of " + ltv + "% exceeds the " + MAX_LTV_PERCENT + "% limit");
        }

        collateralBalance.setAvailableAmount(
                collateralBalance.getAvailableAmount().subtract(collateralAmount));
        collateralBalance.setLockedAmount(collateralBalance.getLockedAmount().add(collateralAmount));
        walletBalanceRepository.save(collateralBalance);

        Loan savedLoan = loanRepository.save(loan);
        transactionRepository.save(createTransaction(
                user,
                TransactionType.BORROW,
                DEFAULT_BORROWED_ASSET,
                borrowedAmount));

        return savedLoan;
    }

    /**
     * Records repayment and releases the loan's collateral back to the user's available balance.
     */
    @Transactional
    public Loan repayLoan(Long loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));
        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new IllegalStateException("Only active loans can be repaid");
        }

        WalletBalance collateralBalance = walletBalanceRepository
                .findByUserIdAndAssetForUpdate(loan.getUser().getId(), loan.getCollateralAsset())
                .orElseThrow(() -> new IllegalStateException("Collateral wallet balance does not exist"));
        if (collateralBalance.getLockedAmount().compareTo(loan.getCollateralAmount()) < 0) {
            throw new IllegalStateException("Locked collateral is less than the loan collateral amount");
        }

        collateralBalance.setLockedAmount(
                collateralBalance.getLockedAmount().subtract(loan.getCollateralAmount()));
        collateralBalance.setAvailableAmount(
                collateralBalance.getAvailableAmount().add(loan.getCollateralAmount()));
        loan.setStatus(LoanStatus.REPAID);

        walletBalanceRepository.save(collateralBalance);
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

    private void validateRequired(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
