package com.hashwhale.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hashwhale.core.config.EarnConfigurationProperties;
import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.EarnPosition;
import com.hashwhale.core.entity.EarnPositionStatus;
import com.hashwhale.core.entity.EarnTermType;
import com.hashwhale.core.entity.Transaction;
import com.hashwhale.core.entity.TransactionStatus;
import com.hashwhale.core.entity.TransactionType;
import com.hashwhale.core.entity.User;
import com.hashwhale.core.entity.WalletBalance;
import com.hashwhale.core.repository.EarnPositionRepository;
import com.hashwhale.core.repository.TransactionRepository;
import com.hashwhale.core.repository.UserRepository;
import com.hashwhale.core.repository.WalletBalanceRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EarnServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 31);

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletBalanceRepository walletBalanceRepository;

    @Mock
    private EarnPositionRepository earnPositionRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PriceService priceService;

    private EarnService earnService;

    @BeforeEach
    void setUp() {
        EarnConfigurationProperties configuration = new EarnConfigurationProperties();
        LinkedHashMap<String, EarnConfigurationProperties.Product> products = new LinkedHashMap<>();
        products.put("USDT_FLEXIBLE", product(Asset.USDT, EarnTermType.FLEXIBLE, "4.50", "10"));
        products.put("USDT_LOCKED_90", product(Asset.USDT, EarnTermType.LOCKED_90, "6.00", "10"));
        configuration.setProducts(products);

        Clock clock = Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC);
        earnService = new EarnService(
                userRepository,
                walletBalanceRepository,
                earnPositionRepository,
                transactionRepository,
                configuration,
                priceService,
                clock);
    }

    @Test
    void calculatesSimpleDailyRewardsAndCapsLockedPositionAtMaturity() {
        EarnPosition position = position(user(1L), EarnTermType.LOCKED_90, "1000", "6.00");
        position.setStartDate(LocalDate.of(2026, 1, 1));
        position.setEndDate(LocalDate.of(2026, 4, 1));

        BigDecimal rewards = earnService.calculateAccruedRewards(position, LocalDate.of(2026, 8, 31));

        assertEquals(0, new BigDecimal("14.794520547945205479").compareTo(rewards));
    }

    @Test
    void subscribeLocksAvailableBalanceAndRecordsACompletedTransaction() {
        User user = user(1L);
        WalletBalance balance = balance(user, Asset.USDT, "1500", "100");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(walletBalanceRepository.findByUserIdAndAssetForUpdate(1L, Asset.USDT))
                .thenReturn(Optional.of(balance));
        when(earnPositionRepository.save(any(EarnPosition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EarnPosition result = earnService.subscribe(1L, "usdt-flexible", new BigDecimal("1000"));

        assertEquals(0, new BigDecimal("500").compareTo(balance.getAvailableAmount()));
        assertEquals(0, new BigDecimal("1100").compareTo(balance.getLockedAmount()));
        assertEquals(Asset.USDT, result.getAsset());
        assertEquals(EarnTermType.FLEXIBLE, result.getTermType());
        assertEquals(TODAY, result.getStartDate());
        assertEquals(null, result.getEndDate());
        assertEquals(EarnPositionStatus.ACTIVE, result.getStatus());

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        Transaction transaction = transactionCaptor.getValue();
        assertEquals(TransactionType.EARN_SUBSCRIBE, transaction.getType());
        assertEquals(TransactionStatus.COMPLETED, transaction.getStatus());
        assertEquals(0, new BigDecimal("1000").compareTo(transaction.getAmount()));
    }

    @Test
    void subscribeRejectsInsufficientBalanceBeforeCreatingPosition() {
        User user = user(1L);
        WalletBalance balance = balance(user, Asset.USDT, "9", "0");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(walletBalanceRepository.findByUserIdAndAssetForUpdate(1L, Asset.USDT))
                .thenReturn(Optional.of(balance));

        assertThrows(
                InsufficientBalanceException.class,
                () -> earnService.subscribe(1L, "USDT_FLEXIBLE", new BigDecimal("10")));

        verify(walletBalanceRepository, never()).save(any());
        verifyNoInteractions(earnPositionRepository, transactionRepository);
    }

    @Test
    void withdrawFlexiblePositionReturnsPrincipalAndAccruedRewards() {
        User user = user(1L);
        EarnPosition position = position(user, EarnTermType.FLEXIBLE, "1000", "6.00");
        position.setStartDate(LocalDate.of(2026, 8, 1));
        WalletBalance balance = balance(user, Asset.USDT, "500", "1000");
        when(earnPositionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(position));
        when(walletBalanceRepository.findByUserIdAndAssetForUpdate(1L, Asset.USDT))
                .thenReturn(Optional.of(balance));
        when(earnPositionRepository.save(position)).thenReturn(position);

        EarnPosition result = earnService.withdraw(10L, 1L);

        BigDecimal expectedReward = new BigDecimal("4.931506849315068493");
        assertEquals(0, BigDecimal.ZERO.compareTo(balance.getLockedAmount()));
        assertEquals(0, new BigDecimal("1504.931506849315068493")
                .compareTo(balance.getAvailableAmount()));
        assertEquals(EarnPositionStatus.WITHDRAWN, result.getStatus());
        assertEquals(TODAY, result.getEndDate());

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        assertEquals(TransactionType.EARN_WITHDRAW, transactionCaptor.getValue().getType());
        assertEquals(0, new BigDecimal("1000").add(expectedReward)
                .compareTo(transactionCaptor.getValue().getAmount()));
    }

    @Test
    void withdrawRejectsLockedPositionBeforeMaturity() {
        User user = user(1L);
        EarnPosition position = position(user, EarnTermType.LOCKED_90, "1000", "6.00");
        position.setStartDate(TODAY);
        position.setEndDate(TODAY.plusDays(90));
        when(earnPositionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(position));

        assertThrows(IllegalStateException.class, () -> earnService.withdraw(10L, 1L));

        verifyNoInteractions(walletBalanceRepository, transactionRepository);
        verify(earnPositionRepository, never()).save(any());
    }

    @Test
    void withdrawRejectsNonOwnerBeforeAccessingWallet() {
        EarnPosition position = position(user(2L), EarnTermType.FLEXIBLE, "1000", "6.00");
        when(earnPositionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(position));

        assertThrows(ForbiddenException.class, () -> earnService.withdraw(10L, 1L));

        verifyNoInteractions(walletBalanceRepository, transactionRepository);
        verify(earnPositionRepository, never()).save(any());
    }

    private EarnConfigurationProperties.Product product(
            Asset asset, EarnTermType termType, String apy, String minimumAmount) {
        EarnConfigurationProperties.Product product = new EarnConfigurationProperties.Product();
        product.setAsset(asset);
        product.setTermType(termType);
        product.setApy(new BigDecimal(apy));
        product.setMinimumAmount(new BigDecimal(minimumAmount));
        product.setActive(true);
        return product;
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

    private EarnPosition position(
            User user, EarnTermType termType, String principal, String apy) {
        EarnPosition position = new EarnPosition();
        position.setId(10L);
        position.setUser(user);
        position.setAsset(Asset.USDT);
        position.setPrincipalAmount(new BigDecimal(principal));
        position.setApy(new BigDecimal(apy));
        position.setTermType(termType);
        position.setStartDate(TODAY);
        position.setStatus(EarnPositionStatus.ACTIVE);
        return position;
    }
}
