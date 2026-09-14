package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.ChargingSite;
import com.evmonitor.domain.ChargingSiteRepository;
import com.evmonitor.domain.ChargingSiteSource;
import com.evmonitor.domain.ChargingSiteUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresChargingSiteRepositoryImpl implements ChargingSiteRepository {

    private final JpaChargingSiteRepository jpa;

    @Override
    public Optional<ChargingSite> findByGeohashAndName(String geohash, String name) {
        return jpa.findByGeohashAndNameKey(geohash, ChargingSite.nameKey(name)).map(this::toDomain);
    }

    @Override
    public ChargingSite save(ChargingSite site) {
        ChargingSiteEntity e = new ChargingSiteEntity();
        e.setId(site.id());
        e.setName(site.name());
        e.setNameKey(ChargingSite.nameKey(site.name()));
        e.setCpoName(site.cpoName());
        e.setGeohash(site.geohash());
        e.setMaxPowerKw(site.maxPowerKw());
        e.setChargePoints(site.chargePoints());
        e.setFastCharging(site.fastCharging());
        e.setSource(site.source().name());
        e.setCreatedAt(site.createdAt());
        return toDomain(jpa.save(e));
    }

    @Override
    public List<ChargingSiteUsage> findRecentlyUsedByUser(UUID userId, int limit) {
        return jpa.findRecentlyUsedByUser(userId, PageRequest.of(0, limit)).stream()
                .map(r -> new ChargingSiteUsage(toDomain(r.getSite()), r.getLastUsedAt(), r.getUsageCount()))
                .toList();
    }

    private ChargingSite toDomain(ChargingSiteEntity e) {
        return new ChargingSite(e.getId(), e.getName(), e.getCpoName(), e.getGeohash(), e.getMaxPowerKw(),
                e.getChargePoints(), e.isFastCharging(), ChargingSiteSource.valueOf(e.getSource()), e.getCreatedAt());
    }
}
