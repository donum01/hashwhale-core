package com.hashwhale.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.Loan;
import com.hashwhale.core.entity.LoanStatus;
import com.hashwhale.core.entity.Transaction;
import com.hashwhale.core.entity.TransactionType;
import com.hashwhale.core.entity.User;
import com.hashwhale.core.entity.WalletBalance;
import com.hashwhale.core.repository.LoanRepository;
import com.hashwhale.core.repository.TransactionRepository;
import com.hashwhale.core.repository.UserRepository;
import com.hashwhale.core.repository.WalletBalanceRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@Rollback
class BorrowSettlementIntegrationTest {

    @Autowired
    private BorrowService borrowService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletBalanceRepository walletBalanceRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void loanLifecycleCreditsPrincipalThenDebitsItWhenCollateralIsReleased() {
        User user = new User();
        user.setEmail("borrow-settlement@example.com");
        user.setPasswordHash("not-used-by-this-test");
        User savedUser = userRepository.saveAndFlush(user);

        walletBalanceRepository.saveAndFlush(balance(savedUser, Asset.BTC, "2", "0"));
        walletBalanceRepository.saveAndFlush(balance(savedUser, Asset.USDT, "500", "0"));

        Loan loan = borrowService.createLoan(
                savedUser.getId(), Asset.BTC, new BigDecimal("1"), new BigDecimal("30000"));
        assertEquals(0, new BigDecimal("2.88").compareTo(loan.getInterestRateApr()));
        entityManager.flush();
        entityManager.clear();

        assertBalance(savedUser.getId(), Asset.BTC, "1", "1");
        assertBalance(savedUser.getId(), Asset.USDT, "30500", "0");

        Loan repaidLoan = borrowService.repayLoan(loan.getId(), savedUser.getId());
        entityManager.flush();
        entityManager.clear();

        assertEquals(LoanStatus.REPAID, loanRepository.findById(repaidLoan.getId()).orElseThrow().getStatus());
        assertBalance(savedUser.getId(), Asset.BTC, "2", "0");
        assertBalance(savedUser.getId(), Asset.USDT, "500", "0");

        List<Transaction> transactions =
                transactionRepository.findByUserIdOrderByCreatedAtDescIdDesc(savedUser.getId());
        assertEquals(2, transactions.size());
        assertEquals(TransactionType.REPAY, transactions.get(0).getType());
        assertEquals(TransactionType.BORROW, transactions.get(1).getType());
    }

    private void assertBalance(
            Long userId, Asset asset, String availableAmount, String lockedAmount) {
        WalletBalance balance = walletBalanceRepository
                .findByUserIdAndAsset(userId, asset)
                .orElseThrow();
        assertEquals(0, new BigDecimal(availableAmount).compareTo(balance.getAvailableAmount()));
        assertEquals(0, new BigDecimal(lockedAmount).compareTo(balance.getLockedAmount()));
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
