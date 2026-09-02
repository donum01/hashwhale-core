package com.hashwhale.core.service;

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
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final UserRepository userRepository;
    private final WalletBalanceRepository walletBalanceRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public List<WalletBalance> getBalances(Long userId) {
        validateUserId(userId);
        return walletBalanceRepository.findByUserId(userId);
    }

    @Transactional
    public WalletBalance deposit(Long userId, Asset asset, BigDecimal amount) {
        validateRequest(userId, asset, amount);
        User user = findUser(userId);

        WalletBalance balance = walletBalanceRepository
                .findByUserIdAndAssetForUpdate(userId, asset)
                .orElseGet(() -> newBalance(user, asset));
        balance.setAvailableAmount(balance.getAvailableAmount().add(amount));

        WalletBalance savedBalance = walletBalanceRepository.save(balance);
        transactionRepository.save(createTransaction(user, TransactionType.DEPOSIT, asset, amount));
        return savedBalance;
    }

    @Transactional
    public WalletBalance withdraw(Long userId, Asset asset, BigDecimal amount) {
        validateRequest(userId, asset, amount);
        User user = findUser(userId);

        WalletBalance balance = walletBalanceRepository
                .findByUserIdAndAssetForUpdate(userId, asset)
                .orElseThrow(() -> new InsufficientBalanceException(
                        "No wallet balance exists for asset " + asset));
        if (balance.getAvailableAmount().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient available " + asset + " balance");
        }

        balance.setAvailableAmount(balance.getAvailableAmount().subtract(amount));
        WalletBalance savedBalance = walletBalanceRepository.save(balance);
        transactionRepository.save(createTransaction(user, TransactionType.WITHDRAW, asset, amount));
        return savedBalance;
    }

    @Transactional(readOnly = true)
    public List<Transaction> getTransactions(Long userId) {
        validateUserId(userId);
        return transactionRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId);
    }

    @Transactional(readOnly = true)
    public Slice<Transaction> getTransactions(
            Long userId,
            Set<TransactionType> types,
            Long beforeId,
            int limit) {
        validateUserId(userId);
        validateHistoryRequest(beforeId, limit);
        PageRequest pageRequest = PageRequest.of(0, limit);
        boolean filtered = types != null && !types.isEmpty();

        if (filtered && beforeId != null) {
            return transactionRepository.findByUserIdAndTypeInAndIdLessThanOrderByIdDesc(
                    userId, types, beforeId, pageRequest);
        }
        if (filtered) {
            return transactionRepository.findByUserIdAndTypeInOrderByIdDesc(
                    userId, types, pageRequest);
        }
        if (beforeId != null) {
            return transactionRepository.findByUserIdAndIdLessThanOrderByIdDesc(
                    userId, beforeId, pageRequest);
        }
        return transactionRepository.findByUserIdOrderByIdDesc(userId, pageRequest);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private WalletBalance newBalance(User user, Asset asset) {
        WalletBalance balance = new WalletBalance();
        balance.setUser(user);
        balance.setAsset(asset);
        balance.setAvailableAmount(BigDecimal.ZERO);
        balance.setLockedAmount(BigDecimal.ZERO);
        return balance;
    }

    private Transaction createTransaction(
            User user, TransactionType type, Asset asset, BigDecimal amount) {
        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setType(type);
        transaction.setAsset(asset);
        transaction.setAmount(amount);
        transaction.setStatus(TransactionStatus.COMPLETED);
        return transaction;
    }

    private void validateRequest(Long userId, Asset asset, BigDecimal amount) {
        validateUserId(userId);
        if (asset == null) {
            throw new IllegalArgumentException("Asset is required");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("User id must be greater than zero");
        }
    }

    private void validateHistoryRequest(Long beforeId, int limit) {
        if (beforeId != null && beforeId <= 0) {
            throw new IllegalArgumentException("History cursor must be greater than zero");
        }
        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("History limit must be between 1 and 50");
        }
    }
}
