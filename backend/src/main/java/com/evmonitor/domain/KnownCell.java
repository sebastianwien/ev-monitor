package com.evmonitor.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Eine Zelle, in der ein Nutzer schon geladen hat - gruppiert aus seinen eigenen Logs, nichts
 * wird dafuer gespeichert. Oeffentliche Zellen haben 7 Stellen (~150 m), private 6 (~600 m).
 *
 * @param cpoName        Anbieter der letzten Ladung in dieser Zelle, null ohne Anbieter
 * @param chargingSiteId Standort der letzten Ladung, null wenn keine Register-Saeule verknuepft war
 * @param lastProviderId Ladekarte der letzten Ladung mit Karte in dieser Zelle, null ohne Karte
 */
public record KnownCell(String geohash, boolean isPublic, long usageCount, LocalDateTime lastUsedAt,
                        String cpoName, UUID chargingSiteId, UUID lastProviderId) {}
