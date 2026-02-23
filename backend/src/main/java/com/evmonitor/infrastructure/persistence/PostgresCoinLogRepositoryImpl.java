package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.CoinLog;
import com.evmonitor.domain.CoinLogRepository;
import com.evmonitor.domain.CoinType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PostgresCoinLogRepositoryImpl implements CoinLogRepository {

    private final JpaCoinLogRepository jpaRepository;

    public PostgresCoinLogRepositoryImpl(JpaCoinLogRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public CoinLog save(CoinLog coinLog) {
        CoinLogEntity entity = toEntity(coinLog);
        CoinLogEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<CoinLog> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<CoinLog> findAllByUserId(UUID userId) {
        return jpaRepository.findAllByUserId(userId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<CoinLog> findAllByUserIdAndCoinType(UUID userId, CoinType coinType) {
        return jpaRepository.findAllByUserIdAndCoinType(userId, coinType).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Integer getTotalCoinsByUserId(UUID userId) {
        return jpaRepository.getTotalCoinsByUserId(userId);
    }

    @Override
    public Integer getTotalCoinsByUserIdAndCoinType(UUID userId, CoinType coinType) {
        return jpaRepository.getTotalCoinsByUserIdAndCoinType(userId, coinType);
    }

    private CoinLogEntity toEntity(CoinLog domain) {
        return new CoinLogEntity(
                domain.getId(),
                domain.getUserId(),
                domain.getCoinType(),
                domain.getAmount(),
                domain.getActionDescription(),
                domain.getCreatedAt()
        );
    }

    private CoinLog toDomain(CoinLogEntity entity) {
        return new CoinLog(
                entity.getId(),
                entity.getUserId(),
                entity.getCoinType(),
                entity.getAmount(),
                entity.getActionDescription(),
                entity.getCreatedAt()
        );
    }
}
