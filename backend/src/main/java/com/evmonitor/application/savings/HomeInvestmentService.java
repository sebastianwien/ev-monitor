package com.evmonitor.application.savings;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/** Speichert die Wallbox-Investition des Nutzers - Grundlage der Amortisationszeile. */
@Service
public class HomeInvestmentService {

    private final JdbcTemplate jdbc;

    public HomeInvestmentService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @param investmentEur null loescht den Wert */
    @Transactional
    public void update(UUID userId, BigDecimal investmentEur) {
        jdbc.update("UPDATE app_user SET home_investment_eur = ? WHERE id = ?", investmentEur, userId);
    }
}
