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
        e.setMaxAcKw(site.maxAcKw());
        e.setMaxDcKw(site.maxDcKw());
        e.setChargePoints(site.chargePoints());
        e.setSource(site.source().name());
        var r = site.register() == null ? ChargingSite.RegisterDetails.NONE : site.register();
        e.setRegisterId(r.registerId());
        e.setStreet(r.street());
        e.setHouseNumber(r.houseNumber());
        e.setPostalCode(r.postalCode());
        e.setCity(r.city());
        e.setPlugTypes(r.plugTypes().isEmpty() ? null : String.join(", ", r.plugTypes()));
        e.setCommissionedOn(r.commissionedOn());
        e.setSiteLabel(r.siteLabel());
        e.setPayment(r.payment());
        e.setOpeningHours(r.openingHours());
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
        var register = new ChargingSite.RegisterDetails(e.getRegisterId(), e.getStreet(), e.getHouseNumber(),
                e.getPostalCode(), e.getCity(),
                e.getPlugTypes() == null ? List.of() : List.of(e.getPlugTypes().split(", ")),
                e.getCommissionedOn(), e.getSiteLabel(), e.getPayment(), e.getOpeningHours());
        return new ChargingSite(e.getId(), e.getName(), e.getCpoName(), e.getGeohash(), e.getMaxAcKw(), e.getMaxDcKw(),
                e.getChargePoints(), ChargingSiteSource.valueOf(e.getSource()), register, e.getCreatedAt());
    }
}
