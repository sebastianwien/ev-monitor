package com.evmonitor.application;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Verschickt den internen Wochenreport jeden Montag um 17:00 Uhr (Europe/Berlin). */
@Component
@RequiredArgsConstructor
public class WeeklyReportScheduler {

    private final WeeklyReportService weeklyReportService;

    @Scheduled(cron = "0 0 17 * * MON", zone = "Europe/Berlin")
    public void sendWeeklyReport() {
        weeklyReportService.send();
    }
}
