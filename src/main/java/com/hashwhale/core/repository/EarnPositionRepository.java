package com.hashwhale.core.repository;

import com.hashwhale.core.entity.EarnPosition;
import com.hashwhale.core.entity.EarnPositionStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EarnPositionRepository extends JpaRepository<EarnPosition, Long> {

    List<EarnPosition> findByUserIdAndStatus(Long userId, EarnPositionStatus status);

    List<EarnPosition> findByUserIdOrderByStartDateDescIdDesc(Long userId);

    Slice<EarnPosition> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    Slice<EarnPosition> findByUserIdAndIdLessThanOrderByIdDesc(
            Long userId, Long beforeId, Pageable pageable);

    Slice<EarnPosition> findByUserIdAndStatusInOrderByIdDesc(
            Long userId, Collection<EarnPositionStatus> statuses, Pageable pageable);

    Slice<EarnPosition> findByUserIdAndStatusInAndIdLessThanOrderByIdDesc(
            Long userId,
            Collection<EarnPositionStatus> statuses,
            Long beforeId,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select position
            from EarnPosition position
            join fetch position.user
            where position.id = :positionId
            """)
    Optional<EarnPosition> findByIdForUpdate(@Param("positionId") Long positionId);
}
