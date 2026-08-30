package com.hashwhale.core.repository;

import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.WalletBalance;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletBalanceRepository extends JpaRepository<WalletBalance, Long> {

    List<WalletBalance> findByUserId(Long userId);

    Optional<WalletBalance> findByUserIdAndAsset(Long userId, Asset asset);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select walletBalance
            from WalletBalance walletBalance
            where walletBalance.user.id = :userId
              and walletBalance.asset = :asset
            """)
    Optional<WalletBalance> findByUserIdAndAssetForUpdate(
            @Param("userId") Long userId, @Param("asset") Asset asset);
}
