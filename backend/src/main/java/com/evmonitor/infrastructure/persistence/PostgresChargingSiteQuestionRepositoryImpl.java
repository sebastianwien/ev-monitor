package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.ChargingSiteQuestion;
import com.evmonitor.domain.ChargingSiteQuestionRepository;
import com.evmonitor.domain.ChargingType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.evmonitor.infrastructure.persistence.ChargingSiteQuestionEntity.UNKNOWN_TYPE;

@Repository
@RequiredArgsConstructor
public class PostgresChargingSiteQuestionRepositoryImpl implements ChargingSiteQuestionRepository {

    private final JpaChargingSiteQuestionRepository jpa;

    /** Eigene Transaktion: die Zuordnung läuft nach dem Commit des Imports, dessen Transaktion noch am Thread hängt. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void openIfAbsent(UUID userId, String geohash, ChargingType chargingType, String candidatesJson) {
        String type = type(chargingType);
        if (jpa.existsByUserIdAndGeohashAndChargingType(userId, geohash, type)) return;
        var e = new ChargingSiteQuestionEntity();
        e.setId(UUID.randomUUID());
        e.setUserId(userId);
        e.setGeohash(geohash);
        e.setChargingType(type);
        e.setCandidates(candidatesJson);
        e.setStatus(ChargingSiteQuestion.Status.OPEN.name());
        e.setCreatedAt(LocalDateTime.now());
        jpa.saveAndFlush(e);
    }

    @Override
    public Optional<ChargingSiteQuestion> findByIdAndUserId(UUID id, UUID userId) {
        return jpa.findByIdAndUserId(id, userId).map(PostgresChargingSiteQuestionRepositoryImpl::toDomain);
    }

    @Override
    public List<ChargingSiteQuestion.WithLogs> findOpenWithLogs(UUID userId) {
        var rows = jpa.findOpenWithLogs(userId);
        if (rows.isEmpty()) return List.of();
        Map<UUID, ChargingSiteQuestionEntity> byId = jpa.findAllById(
                        rows.stream().map(JpaChargingSiteQuestionRepository.OpenRow::getQuestionId).toList())
                .stream().collect(Collectors.toMap(ChargingSiteQuestionEntity::getId, Function.identity()));
        return rows.stream()
                .filter(r -> byId.containsKey(r.getQuestionId()))
                .map(r -> new ChargingSiteQuestion.WithLogs(toDomain(byId.get(r.getQuestionId())),
                        (int) r.getLogCount(), r.getLastLoggedAt()))
                .toList();
    }

    @Override
    public List<UUID> findOpenLogIds(ChargingSiteQuestion q) {
        return jpa.findOpenLogIds(q.userId(), q.geohash(), type(q.chargingType()));
    }

    @Override
    @Transactional
    public boolean close(UUID id, UUID userId, ChargingSiteQuestion.Status status) {
        return jpa.close(id, userId, status.name(), LocalDateTime.now()) > 0;
    }

    private static String type(ChargingType chargingType) {
        return chargingType == null ? UNKNOWN_TYPE : chargingType.name();
    }

    private static ChargingSiteQuestion toDomain(ChargingSiteQuestionEntity e) {
        ChargingType type = UNKNOWN_TYPE.equals(e.getChargingType()) ? null : ChargingType.valueOf(e.getChargingType());
        return new ChargingSiteQuestion(e.getId(), e.getUserId(), e.getGeohash(), type, e.getCandidates(),
                ChargingSiteQuestion.Status.valueOf(e.getStatus()));
    }
}
