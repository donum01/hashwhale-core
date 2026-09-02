package com.hashwhale.core.repository;

import com.hashwhale.core.entity.Transaction;
import com.hashwhale.core.entity.TransactionStatus;
import com.hashwhale.core.entity.TransactionType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    Slice<Transaction> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    Slice<Transaction> findByUserIdAndIdLessThanOrderByIdDesc(
            Long userId, Long beforeId, Pageable pageable);

    Slice<Transaction> findByUserIdAndTypeInOrderByIdDesc(
            Long userId, Collection<TransactionType> types, Pageable pageable);

    Slice<Transaction> findByUserIdAndTypeInAndIdLessThanOrderByIdDesc(
            Long userId, Collection<TransactionType> types, Long beforeId, Pageable pageable);

    List<Transaction> findTop5ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    List<Transaction> findByUserIdAndStatus(Long userId, TransactionStatus status);

    List<Transaction> findByUserIdAndType(Long userId, TransactionType type);
}
