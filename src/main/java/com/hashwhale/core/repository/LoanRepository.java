package com.hashwhale.core.repository;

import com.hashwhale.core.entity.Loan;
import com.hashwhale.core.entity.LoanStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    List<Loan> findByUserId(Long userId);

    List<Loan> findByUserIdAndStatus(Long userId, LoanStatus status);
}
