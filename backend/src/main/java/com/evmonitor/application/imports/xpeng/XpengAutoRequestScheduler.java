package com.evmonitor.application.imports.xpeng;

import com.evmonitor.infrastructure.persistence.xpeng.XpengConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class XpengAutoRequestScheduler {

    private final XpengConnectionService connectionService;
    private final XpengOutboundMailService outboundMailService;

    // Taglich 06:00 UTC - prueft alle Connections ob die 14-Tage-Frist abgelaufen ist
    @Scheduled(cron = "0 0 6 * * *")
    public void sendPendingRequests() {
        List<XpengConnection> due = connectionService.getConnectionsDueForAutoRequest();
        if (due.isEmpty()) {
            log.debug("XPeng AutoSync: keine faelligen Anfragen");
            return;
        }
        log.info("XPeng AutoSync: {} Connections faellig fuer DA-Anfrage", due.size());
        for (XpengConnection conn : due) {
            try {
                outboundMailService.sendDataRequest(conn);
            } catch (Exception e) {
                log.error("XPeng AutoSync: Anfrage fehlgeschlagen fuer connection={}", conn.getId(), e);
            }
        }
    }
}
