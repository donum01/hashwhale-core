package com.hashwhale.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.Loan;
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

    @BeforeEach
    void setUp() {
        borrowService = new BorrowService(
                userRepository,
                walletBalanceRepository,
                loanRepository,
                transactionRepository,
                priceService);
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
    void createLoanRejectsWhenLtvExceedsSeventyPercent() {
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
                        1L, Asset.BTC, new BigDecimal("1"), new BigDecimal("42000.01")));

        assertEquals(0, BigDecimal.ONE.compareTo(walletBalance.getAvailableAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(walletBalance.getLockedAmount()));
        verify(walletBalanceRepository, never()).save(walletBalance);
        verify(loanRepository, never()).save(org.mockito.ArgumentMatchers.any(Loan.class));
        verify(transactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
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
}
