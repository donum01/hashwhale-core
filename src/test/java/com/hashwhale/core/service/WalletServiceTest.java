package com.hashwhale.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.Transaction;
import com.hashwhale.core.entity.TransactionStatus;
import com.hashwhale.core.entity.TransactionType;
import com.hashwhale.core.entity.User;
import com.hashwhale.core.entity.WalletBalance;
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
class WalletServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletBalanceRepository walletBalanceRepository;

    @Mock
    private TransactionRepository transactionRepository;

    private WalletService walletService;

    @BeforeEach
    void setUp() {
        walletService = new WalletService(userRepository, walletBalanceRepository, transactionRepository);
    }

    @Test
    void depositCreatesFirstAssetBalanceAndCompletedTransaction() {
        User user = user(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(walletBalanceRepository.findByUserIdAndAssetForUpdate(1L, Asset.ETH))
                .thenReturn(Optional.empty());
        when(walletBalanceRepository.save(any(WalletBalance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WalletBalance result = walletService.deposit(1L, Asset.ETH, new BigDecimal("2.5"));

        assertEquals(0, new BigDecimal("2.5").compareTo(result.getAvailableAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getLockedAmount()));
        assertEquals(Asset.ETH, result.getAsset());
        assertEquals(user, result.getUser());

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        Transaction transaction = transactionCaptor.getValue();
        assertEquals(TransactionType.DEPOSIT, transaction.getType());
        assertEquals(TransactionStatus.COMPLETED, transaction.getStatus());
        assertEquals(Asset.ETH, transaction.getAsset());
        assertEquals(0, new BigDecimal("2.5").compareTo(transaction.getAmount()));
    }

    @Test
    void withdrawUsesLockedBalanceAndRecordsCompletedTransaction() {
        User user = user(1L);
        WalletBalance balance = balance(user, Asset.USDT, "500", "25");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(walletBalanceRepository.findByUserIdAndAssetForUpdate(1L, Asset.USDT))
                .thenReturn(Optional.of(balance));
        when(walletBalanceRepository.save(balance)).thenReturn(balance);

        WalletBalance result = walletService.withdraw(1L, Asset.USDT, new BigDecimal("125"));

        assertEquals(0, new BigDecimal("375").compareTo(result.getAvailableAmount()));
        assertEquals(0, new BigDecimal("25").compareTo(result.getLockedAmount()));

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        assertEquals(TransactionType.WITHDRAW, transactionCaptor.getValue().getType());
        assertEquals(TransactionStatus.COMPLETED, transactionCaptor.getValue().getStatus());
    }

    @Test
    void withdrawRejectsInsufficientAvailableBalanceWithoutWriting() {
        User user = user(1L);
        WalletBalance balance = balance(user, Asset.BTC, "0.25", "1.00");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(walletBalanceRepository.findByUserIdAndAssetForUpdate(1L, Asset.BTC))
                .thenReturn(Optional.of(balance));

        assertThrows(
                InsufficientBalanceException.class,
                () -> walletService.withdraw(1L, Asset.BTC, new BigDecimal("0.50")));

        assertEquals(0, new BigDecimal("0.25").compareTo(balance.getAvailableAmount()));
        verify(walletBalanceRepository, never()).save(any(WalletBalance.class));
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void depositRejectsNonPositiveAmountBeforeRepositoryAccess() {
        assertThrows(
                IllegalArgumentException.class,
                () -> walletService.deposit(1L, Asset.USDC, BigDecimal.ZERO));

        verifyNoInteractions(userRepository, walletBalanceRepository, transactionRepository);
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
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
}
