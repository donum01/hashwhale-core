package com.hashwhale.core.repository;

import com.hashwhale.core.entity.Loan;
import com.hashwhale.core.entity.LoanStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    List<Loan> findByUserId(Long userId);

    List<Loan> findByUserIdAndStatus(Long userId, LoanStatus status);

    Slice<Loan> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    Slice<Loan> findByUserIdAndIdLessThanOrderByIdDesc(
            Long userId, Long beforeId, Pageable pageable);

    Slice<Loan> findByUserIdAndStatusInOrderByIdDesc(
            Long userId, Collection<LoanStatus> statuses, Pageable pageable);

    Slice<Loan> findByUserIdAndStatusInAndIdLessThanOrderByIdDesc(
            Long userId, Collection<LoanStatus> statuses, Long beforeId, Pageable pageable);
}
