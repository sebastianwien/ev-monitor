package com.evmonitor.application.vehiclespec;

import com.evmonitor.domain.User;
import com.evmonitor.domain.vehiclespec.MissingVehicleRequest;
import com.evmonitor.domain.vehiclespec.MissingVehicleRequestRepository;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DSGVO-Zusage im Dialog: "Deine Mail-Adresse nutze ich nur dafür und lösche sie danach."
 * Das muss auch für Meldungen gelten, die nie erledigt werden (Spam, Duplikat, kein echtes Auto).
 */
class MissingVehicleRequestPurgeIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MissingVehicleRequestRepository repository;
    @Autowired
    private MissingVehicleRequestPurgeScheduler scheduler;

    private MissingVehicleRequest openRequestCreatedDaysAgo(int days) {
        User user = createAndSaveUser("purge-" + System.nanoTime() + "@example.com");
        MissingVehicleRequest r = MissingVehicleRequest.create(user.getId(),
                new MissingVehicleRequest.Draft("kontakt@example.com", "Lucid Air " + System.nanoTime(),
                        null, null, null, null));
        ReflectionTestUtils.setField(r, "createdAt", LocalDateTime.now().minusDays(days));
        return repository.save(r);
    }

    @Test
    void openRequestOlderThanRetention_losesContactEmail() {
        MissingVehicleRequest stale = openRequestCreatedDaysAgo(MissingVehicleRequestPurgeScheduler.OPEN_RETENTION_DAYS + 1);

        scheduler.purgeContactEmails();

        assertThat(repository.findById(stale.getId()).orElseThrow().getContactEmail()).isNull();
    }

    @Test
    void openRequestWithinRetention_keepsContactEmail() {
        MissingVehicleRequest fresh = openRequestCreatedDaysAgo(10);

        scheduler.purgeContactEmails();

        assertThat(repository.findById(fresh.getId()).orElseThrow().getContactEmail()).isEqualTo("kontakt@example.com");
    }
}
