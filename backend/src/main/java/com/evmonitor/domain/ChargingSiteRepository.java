package com.evmonitor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChargingSiteRepository {

    /** Standort in dieser Zelle mit diesem Namen (Vergleich ueber {@link ChargingSite#nameKey}). */
    Optional<ChargingSite> findByGeohashAndName(String geohash, String name);

    ChargingSite save(ChargingSite site);

    /** Standorte, an denen der Nutzer geladen hat, zuletzt genutzte zuerst. */
    List<ChargingSiteUsage> findRecentlyUsedByUser(UUID userId, int limit);
}
