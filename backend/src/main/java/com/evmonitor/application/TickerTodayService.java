package com.evmonitor.application;

import com.evmonitor.infrastructure.persistence.LeaderboardQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;

/**
 * "Heute"-Einträge im Community-Ticker: einzelne öffentliche Ladungen anderer Nutzer von heute,
 * ohne Name, Auto, Ort und Uhrzeit. Eigener 15-Minuten-Cache, getrennt vom 4-h-Ticker, damit
 * abends nicht "heute" für gestern steht und ein Opt-out schnell wirkt.
 */
@Service
@RequiredArgsConstructor
public class TickerTodayService {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final int MAX_ITEMS = 2;

    private final LeaderboardQueryRepository queryRepository;

    @Cacheable(value = "tickerToday", key = "'today'")
    public List<TickerItemDTO> getTodayItems() {
        // logged_at liegt als UTC-Wanduhrzeit in der DB, "heute" ist der Berliner Kalendertag.
        LocalDate today = LocalDate.now(BERLIN);
        LocalDateTime start = toUtc(today);
        LocalDateTime end = toUtc(today.plusDays(1));

        Set<UUID> seenUsers = new HashSet<>();
        List<TickerItemDTO> items = new ArrayList<>();
        for (TodayChargeRow row : queryRepository.getTodayPublicCharges(start, end)) {
            if (items.size() == MAX_ITEMS) break;
            if (!seenUsers.add(row.userId())) continue; // höchstens eine Ladung je Nutzer
            items.add(toItem(row));
        }
        return items;
    }

    private static TickerItemDTO toItem(TodayChargeRow row) {
        Map<String, String> params = new HashMap<>();
        params.put("kwh", row.kwh().setScale(0, RoundingMode.HALF_UP).toPlainString());
        params.put("cost", row.costEur().setScale(2, RoundingMode.HALF_UP).toPlainString());
        params.put("ct", row.costEur().multiply(BigDecimal.valueOf(100))
                .divide(row.kwh(), 0, RoundingMode.HALF_UP).toPlainString());
        if (row.provider() == null) {
            return TickerItemDTO.stat("today_charge_anon", "money", Map.copyOf(params));
        }
        params.put("provider", row.provider());
        return TickerItemDTO.stat("today_charge", "money", Map.copyOf(params));
    }

    private static LocalDateTime toUtc(LocalDate berlinDay) {
        return berlinDay.atStartOfDay(BERLIN).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
