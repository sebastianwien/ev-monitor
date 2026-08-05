package com.evmonitor.infrastructure.persistence.xpeng;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface XpengReceivedMailRepository extends JpaRepository<XpengReceivedMail, UUID> {
    boolean existsByMessageId(String messageId);
    List<XpengReceivedMail> findByConnectionIdOrderByReceivedAtDesc(UUID connectionId);
    /**
     * Alle gespeicherten Passwoerter einer Connection, neuestes zuerst. XPeng schickt
     * XLSX und Passwort getrennt und nicht immer in derselben Reihenfolge - der Poller
     * probiert deshalb alle Kandidaten durch, nicht nur das juengste.
     */
    List<XpengReceivedMail> findByConnectionIdAndExtractedPasswordIsNotNullOrderByReceivedAtDesc(UUID connectionId);
}
