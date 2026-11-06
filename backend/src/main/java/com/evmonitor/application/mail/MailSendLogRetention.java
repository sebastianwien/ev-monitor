package com.evmonitor.application.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Versandprotokoll höchstens 12 Monate aufbewahren. Laufen beide Blue/Green-Instanzen, löscht die zweite nichts mehr. */
@Slf4j
@Component
public class MailSendLogRetention {

    static final int KEEP_DAYS = 365;

    private final MailSendLog sendLog;

    public MailSendLogRetention(MailSendLog sendLog) {
        this.sendLog = sendLog;
    }

    @Scheduled(cron = "0 20 3 * * *")
    public void purge() {
        int deleted = sendLog.deleteOlderThan(LocalDateTime.now().minusDays(KEEP_DAYS));
        if (deleted > 0) log.info("Mail send log: {} entries older than {} days deleted", deleted, KEEP_DAYS);
    }
}
