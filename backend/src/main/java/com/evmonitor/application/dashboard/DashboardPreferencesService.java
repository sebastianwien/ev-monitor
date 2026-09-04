package com.evmonitor.application.dashboard;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Welche Dashboard-Kacheln ein Nutzer ausgeblendet hat.
 *
 * Die Sichtbarkeit ist eine Aussage ueber den Nutzer, nicht ueber das Geraet - sie gehoert
 * deshalb auf den Server und nicht in den localStorage, damit die Kachel nicht auf jedem
 * neuen Geraet wieder auftaucht.
 */
@Service
public class DashboardPreferencesService {

    private final JdbcTemplate jdbc;

    public DashboardPreferencesService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isSavingsCardDismissed(UUID userId) {
        Boolean dismissed = jdbc.queryForObject(
                "SELECT savings_card_dismissed FROM app_user WHERE id = ?", Boolean.class, userId);
        return Boolean.TRUE.equals(dismissed);
    }

    @Transactional
    public void setSavingsCardDismissed(UUID userId, boolean dismissed) {
        jdbc.update("UPDATE app_user SET savings_card_dismissed = ? WHERE id = ?", dismissed, userId);
    }
}
