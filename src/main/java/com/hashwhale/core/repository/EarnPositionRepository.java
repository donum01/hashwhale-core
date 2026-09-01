package com.hashwhale.core.repository;

import com.hashwhale.core.entity.EarnPosition;
import com.hashwhale.core.entity.EarnPositionStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EarnPositionRepository extends JpaRepository<EarnPosition, Long> {

    List<EarnPosition> findByUserIdAndStatus(Long userId, EarnPositionStatus status);

    List<EarnPosition> findByUserIdOrderByStartDateDescIdDesc(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select position
            from EarnPosition position
            join fetch position.user
            where position.id = :positionId
            """)
    Optional<EarnPosition> findByIdForUpdate(@Param("positionId") Long positionId);
}
