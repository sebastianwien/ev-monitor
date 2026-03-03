package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.CoinType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface JpaCoinLogRepository extends JpaRepository<CoinLogEntity, UUID> {

    List<CoinLogEntity> findAllByUserId(UUID userId);

    List<CoinLogEntity> findAllByUserIdAndCoinType(UUID userId, CoinType coinType);

    boolean existsByUserIdAndActionDescription(UUID userId, String actionDescription);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM CoinLogEntity c WHERE c.userId = :userId")
    Integer getTotalCoinsByUserId(@Param("userId") UUID userId);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM CoinLogEntity c WHERE c.userId = :userId AND c.coinType = :coinType")
    Integer getTotalCoinsByUserIdAndCoinType(@Param("userId") UUID userId, @Param("coinType") CoinType coinType);
}
