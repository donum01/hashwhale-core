package com.hashwhale.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BorrowServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletBalanceRepository walletBalanceRepository;

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PriceService priceService;

    private BorrowService borrowService;
    private BorrowConfigurationProperties configuration;

    @BeforeEach
    void setUp() {
        configuration = new BorrowConfigurationProperties();
        configuration.setInterestRateApr(new BigDecimal("2.88"));
        configuration.setMaxLtvPercent(new BigDecimal("70"));
        configuration.setWarningLtvPercent(new BigDecimal("50"));
        configuration.setLiquidationLtvPercent(new BigDecimal("85"));
        borrowService = new BorrowService(
                userRepository,
                walletBalanceRepository,
                loanRepository,
                transactionRepository,
                priceService,
                configuration);
    }

    @Test
    void calculateLtvReturnsPercentageUsingUsdValues() {
        Loan loan = new Loan();
        loan.setCollateralAmount(new BigDecimal("2"));
        loan.setBorrowedAmount(new BigDecimal("30000"));
        loan.setBorrowedAsset(Asset.USDT);
        when(priceService.getUsdPrice(Asset.USDT)).thenReturn(BigDecimal.ONE);

        BigDecimal ltv = borrowService.calculateLtv(loan, new BigDecimal("60000"));

        assertEquals(0, new BigDecimal("25.00000000").compareTo(ltv));
    }

    @Test
    void createLoanRejectsWhenAvailableCollateralIsInsufficient() {
        User user = user(1L);
        WalletBalance walletBalance = walletBalance(Asset.BTC, "0.5", "0");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(walletBalanceRepository.findByUserIdAndAssetForUpdate(1L, Asset.BTC))
                .thenReturn(Optional.of(walletBalance));

        assertThrows(
                InsufficientBalanceException.class,
                () -> borrowService.createLoan(
                        1L, Asset.BTC, new BigDecimal("1"), new BigDecimal("10000")));

        assertEquals(0, new BigDecimal("0.5").compareTo(walletBalance.getAvailableAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(walletBalance.getLockedAmount()));
        verify(loanRepository, never()).save(org.mockito.ArgumentMatchers.any(Loan.class));
        verify(transactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createLoanRejectsUnsupportedCollateralAssetBeforeRepositoryAccess() {
        assertThrows(
                IllegalArgumentException.class,
                () -> borrowService.createLoan(
                        1L, Asset.USDT, new BigDecimal("1000"), new BigDecimal("500")));

        verifyNoInteractions(
                userRepository,
                walletBalanceRepository,
                loanRepository,
                transactionRepository,
                priceService);
    }

    @Test
    void createLoanRejectsWhenLtvExceedsConfiguredMaximum() {
        configuration.setMaxLtvPercent(new BigDecimal("60"));
        User user = user(1L);
        WalletBalance walletBalance = walletBalance(Asset.BTC, "1", "0");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(walletBalanceRepository.findByUserIdAndAssetForUpdate(1L, Asset.BTC))
                .thenReturn(Optional.of(walletBalance));
        when(priceService.getUsdPrice(Asset.BTC)).thenReturn(new BigDecimal("60000"));
        when(priceService.getUsdPrice(Asset.USDT)).thenReturn(BigDecimal.ONE);

        assertThrows(
                LtvLimitExceededException.class,
                () -> borrowService.createLoan(
                        1L, Asset.BTC, new BigDecimal("1"), new BigDecimal("36000.01")));

        assertEquals(0, BigDecimal.ONE.compareTo(walletBalance.getAvailableAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(walletBalance.getLockedAmount()));
        verify(walletBalanceRepository, never()).save(walletBalance);
        verify(loanRepository, never()).save(org.mockito.ArgumentMatchers.any(Loan.class));
        verify(transactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createLoanLocksCollateralAndCreditsBorrowedAssetBalance() {
        User user = user(1L);
        WalletBalance collateralBalance = walletBalance(Asset.BTC, "2", "0");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(walletBalanceRepository.findByUserIdAndAssetForUpdate(1L, Asset.BTC))
                .thenReturn(Optional.of(collateralBalance));
        when(walletBalanceRepository.findByUserIdAndAssetForUpdate(1L, Asset.USDT))
                .thenReturn(Optional.empty());
        when(priceService.getUsdPrice(Asset.BTC)).thenReturn(new BigDecimal("60000"));
        when(priceService.getUsdPrice(Asset.USDT)).thenReturn(BigDecimal.ONE);
        when(walletBalanceRepository.save(org.mockito.ArgumentMatchers.any(WalletBalance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(loanRepository.save(org.mockito.ArgumentMatchers.any(Loan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Loan result = borrowService.createLoan(
                1L, Asset.BTC, new BigDecimal("1"), new BigDecimal("30000"));

        assertEquals(0, BigDecimal.ONE.compareTo(collateralBalance.getAvailableAmount()));
        assertEquals(0, BigDecimal.ONE.compareTo(collateralBalance.getLockedAmount()));
        assertEquals(LoanStatus.ACTIVE, result.getStatus());
        assertEquals(0, new BigDecimal("2.88").compareTo(result.getInterestRateApr()));

        ArgumentCaptor<WalletBalance> balanceCaptor = ArgumentCaptor.forClass(WalletBalance.class);
        verify(walletBalanceRepository, org.mockito.Mockito.times(2)).save(balanceCaptor.capture());
        WalletBalance usdtBalance = balanceCaptor.getAllValues().stream()
                .filter(balance -> balance.getAsset() == Asset.USDT)
                .findFirst()
                .orElseThrow();
        assertEquals(0, new BigDecimal("30000").compareTo(usdtBalance.getAvailableAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(usdtBalance.getLockedAmount()));

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        Transaction transaction = transactionCaptor.getValue();
        assertEquals(TransactionType.BORROW, transaction.getType());
        assertEquals(TransactionStatus.COMPLETED, transaction.getStatus());
        assertEquals(Asset.USDT, transaction.getAsset());
        assertEquals(0, new BigDecimal("30000").compareTo(transaction.getAmount()));
    }

    @Test
    void repayLoanDeductsBorrowedAssetAndUnlocksCollateral() {
        User owner = user(1L);
        Loan loan = activeLoan(owner, "0.5", "10000");
        WalletBalance collateralBalance = walletBalance(Asset.BTC, "0.5", "0.5");
        WalletBalance repaymentBalance = walletBalance(Asset.USDT, "12000", "0");
        when(loanRepository.findById(10L)).thenReturn(Optional.of(loan));
        when(walletBalanceRepository.findByUserIdAndAssetForUpdate(1L, Asset.BTC))
                .thenReturn(Optional.of(collateralBalance));
        when(walletBalanceRepository.findByUserIdAndAssetForUpdate(1L, Asset.USDT))
                .thenReturn(Optional.of(repaymentBalance));
        when(walletBalanceRepository.save(org.mockito.ArgumentMatchers.any(WalletBalance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(loanRepository.save(loan)).thenReturn(loan);

        Loan result = borrowService.repayLoan(10L, 1L);

        assertEquals(0, BigDecimal.ONE.compareTo(collateralBalance.getAvailableAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(collateralBalance.getLockedAmount()));
        assertEquals(0, new BigDecimal("2000").compareTo(repaymentBalance.getAvailableAmount()));
        assertEquals(LoanStatus.REPAID, result.getStatus());

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        Transaction transaction = transactionCaptor.getValue();
        assertEquals(TransactionType.REPAY, transaction.getType());
        assertEquals(TransactionStatus.COMPLETED, transaction.getStatus());
        assertEquals(Asset.USDT, transaction.getAsset());
        assertEquals(0, new BigDecimal("10000").compareTo(transaction.getAmount()));
    }

    @Test
    void repayLoanRejectsInsufficientRepaymentFundsWithoutChangingBalances() {
        User owner = user(1L);
        Loan loan = activeLoan(owner, "0.5", "10000");
        WalletBalance collateralBalance = walletBalance(Asset.BTC, "0.5", "0.5");
        WalletBalance repaymentBalance = walletBalance(Asset.USDT, "9999.99", "0");
        when(loanRepository.findById(10L)).thenReturn(Optional.of(loan));
        when(walletBalanceRepository.findByUserIdAndAssetForUpdate(1L, Asset.BTC))
                .thenReturn(Optional.of(collateralBalance));
        when(walletBalanceRepository.findByUserIdAndAssetForUpdate(1L, Asset.USDT))
                .thenReturn(Optional.of(repaymentBalance));

        assertThrows(InsufficientBalanceException.class, () -> borrowService.repayLoan(10L, 1L));

        assertEquals(0, new BigDecimal("0.5").compareTo(collateralBalance.getAvailableAmount()));
        assertEquals(0, new BigDecimal("0.5").compareTo(collateralBalance.getLockedAmount()));
        assertEquals(0, new BigDecimal("9999.99").compareTo(repaymentBalance.getAvailableAmount()));
        assertEquals(LoanStatus.ACTIVE, loan.getStatus());
        verify(walletBalanceRepository, never()).save(org.mockito.ArgumentMatchers.any(WalletBalance.class));
        verify(loanRepository, never()).save(org.mockito.ArgumentMatchers.any(Loan.class));
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void repayLoanRejectsNonOwnerBeforeChangingCollateralOrLoan() {
        User owner = user(2L);
        Loan loan = new Loan();
        loan.setUser(owner);
        when(loanRepository.findById(10L)).thenReturn(Optional.of(loan));

        assertThrows(ForbiddenException.class, () -> borrowService.repayLoan(10L, 1L));

        verifyNoInteractions(walletBalanceRepository, transactionRepository);
        verify(loanRepository, never()).save(org.mockito.ArgumentMatchers.any(Loan.class));
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private WalletBalance walletBalance(Asset asset, String availableAmount, String lockedAmount) {
        WalletBalance walletBalance = new WalletBalance();
        walletBalance.setAsset(asset);
        walletBalance.setAvailableAmount(new BigDecimal(availableAmount));
        walletBalance.setLockedAmount(new BigDecimal(lockedAmount));
        return walletBalance;
    }

    private Loan activeLoan(User owner, String collateralAmount, String borrowedAmount) {
        Loan loan = new Loan();
        loan.setUser(owner);
        loan.setCollateralAsset(Asset.BTC);
        loan.setCollateralAmount(new BigDecimal(collateralAmount));
        loan.setBorrowedAsset(Asset.USDT);
        loan.setBorrowedAmount(new BigDecimal(borrowedAmount));
        loan.setInterestRateApr(BigDecimal.ZERO);
        loan.setStatus(LoanStatus.ACTIVE);
        return loan;
    }
}
