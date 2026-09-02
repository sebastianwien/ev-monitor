package com.evmonitor.application.savings;

import com.evmonitor.application.savings.HomeChargingSavingsService.HomeChargingProfile;
import com.evmonitor.application.savings.HomeChargingSavingsService.HomeChargingProfileProvider;
import com.evmonitor.infrastructure.persistence.ChargingSavingsQueryRepository;
import com.evmonitor.infrastructure.persistence.ChargingSavingsQueryRepository.UserProfileRow;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Laedt die Stammdaten der Ersparnis-Rechnung aus Nutzer und Ladekarten. */
@Component
public class JdbcHomeChargingProfileProvider implements HomeChargingProfileProvider {

    private final ChargingSavingsQueryRepository repository;

    public JdbcHomeChargingProfileProvider(ChargingSavingsQueryRepository repository) {
        this.repository = repository;
    }

    @Override
    public HomeChargingProfile forUser(UUID userId) {
        UserProfileRow row = repository.profile(userId);
        return new HomeChargingProfile(row.country(), row.investmentEur());
    }
}
