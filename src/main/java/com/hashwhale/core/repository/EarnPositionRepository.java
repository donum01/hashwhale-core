package com.hashwhale.core.repository;

import com.hashwhale.core.entity.EarnPosition;
import com.hashwhale.core.entity.EarnPositionStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EarnPositionRepository extends JpaRepository<EarnPosition, Long> {

    List<EarnPosition> findByUserIdAndStatus(Long userId, EarnPositionStatus status);
}
