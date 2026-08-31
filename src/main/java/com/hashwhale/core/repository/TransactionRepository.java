package com.hashwhale.core.repository;

import com.hashwhale.core.entity.Transaction;
import com.hashwhale.core.entity.TransactionStatus;
import com.hashwhale.core.entity.TransactionType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    List<Transaction> findByUserIdAndStatus(Long userId, TransactionStatus status);

    List<Transaction> findByUserIdAndType(Long userId, TransactionType type);
}
