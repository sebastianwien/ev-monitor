package com.evmonitor.application;

import com.evmonitor.domain.ChargingSite;
import com.evmonitor.domain.ChargingSiteAttachment;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Schreibt eine Register-Säule an eine Ladung - für die automatische Zuordnung und für die Antwort
 * des Nutzers auf die Frage nach der Säule. Von der Säule kommen Ort, Betreiber und "öffentlich";
 * Preis und Karte danach aus {@link LocationPricing}, das jetzt die Historie an der Säule findet.
 * Was die Ladung schon hat, bleibt stehen (siehe {@link EvLogRepository#attachChargingSite}).
 */
@Component
public class ChargingSiteAttacher {

    private final EvLogRepository evLogRepository;
    private final ChargingSiteService chargingSiteService;
    private final LocationPricing locationPricing;
    private final TransactionTemplate transaction;

    public ChargingSiteAttacher(EvLogRepository evLogRepository, ChargingSiteService chargingSiteService,
                                LocationPricing locationPricing, PlatformTransactionManager transactionManager) {
        this.evLogRepository = evLogRepository;
        this.chargingSiteService = chargingSiteService;
        this.locationPricing = locationPricing;
        // Immer eine eigene Transaktion: aus afterCommit heraus hängt die abgeschlossene noch am
        // Thread, und was dort hineingeschrieben wird, committet niemand mehr.
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Preis wie bei einer neuen Ladung an dieser Säule, geschrieben als gezieltes Update: parallel
     * nachgetragene Werte (Temperatur) und alles, was die Ladung schon hat, bleiben stehen.
     *
     * @param owner Besitzer der Ladung - Preis und Karte stammen aus seiner Historie
     * @return false, wenn die Ladung inzwischen eine Säule hat, privat ist oder ihren Ort geändert hat
     */
    public boolean attach(EvLog evLog, UUID owner, NearbyStation station) {
        return Boolean.TRUE.equals(transaction.execute(status -> {
            ChargingSite site = chargingSiteService.siteFor(station);
            EvLog priced = locationPricing.enrich(evLog.toBuilder()
                    .chargingSiteId(site.id()).geohash(site.geohash()).publicCharging(true).build(), owner);
            return evLogRepository.attachChargingSite(new ChargingSiteAttachment(evLog.getId(), evLog.getGeohash(),
                    site.id(), site.geohash(), site.cpoNameForLog(), priced.getCostEur(), priced.getPricePerKwh(),
                    priced.getChargingProviderId()));
        }));
    }
}
