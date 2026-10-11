package com.evmonitor.infrastructure.persistence;

import com.evmonitor.application.ingest.ChargingEntry;
import com.evmonitor.application.ingest.IngestCommand;
import com.evmonitor.application.ingest.IngestDoor;
import com.evmonitor.application.ingest.IngestGateway;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.RouteType;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V203 removes HIGHWAY and CITY that live sources only carried because they copied them from the
 * charge before. Flyway runs it on an empty schema, so the test runs the script on prepared rows.
 */
class InheritedRouteTypeMigrationIT extends AbstractIntegrationTest {

    @Autowired
    private IngestGateway gateway;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void dropsOnlyCopiedHighwayAndCityOfLiveSources() throws Exception {
        User user = createAndSaveUser("v203-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        UUID first = charge(user, car, DataSource.SMARTCAR_LIVE, 8, RouteType.HIGHWAY);     // no charge before: kept
        UUID copied = charge(user, car, DataSource.SMARTCAR_LIVE, 9, RouteType.HIGHWAY);    // same as before: dropped
        UUID chosen = charge(user, car, DataSource.TESLA_LIVE, 10, RouteType.CITY);         // differs: set on purpose, kept
        UUID wallbox = charge(user, car, DataSource.WALLBOX_GOE, 11, RouteType.CITY);       // copied: dropped
        UUID combined = charge(user, car, DataSource.WALLBOX_GOE, 12, RouteType.COMBINED);  // COMBINED stays
        UUID combinedAgain = charge(user, car, DataSource.WALLBOX_GOE, 13, RouteType.COMBINED);
        UUID manual = charge(user, car, DataSource.USER_LOGGED, 14, RouteType.HIGHWAY);
        UUID manualAgain = charge(user, car, DataSource.USER_LOGGED, 15, RouteType.HIGHWAY); // user source: untouched

        jdbc.execute(new ClassPathResource("db/migration/V203__drop_inherited_route_types.sql").getContentAsString(StandardCharsets.UTF_8));

        assertThat(routeType(first)).isEqualTo(RouteType.HIGHWAY);
        assertThat(routeType(copied)).isNull();
        assertThat(routeType(chosen)).isEqualTo(RouteType.CITY);
        assertThat(routeType(wallbox)).isNull();
        assertThat(routeType(combined)).isEqualTo(RouteType.COMBINED);
        assertThat(routeType(combinedAgain)).isEqualTo(RouteType.COMBINED);
        assertThat(routeType(manual)).isEqualTo(RouteType.HIGHWAY);
        assertThat(routeType(manualAgain)).isEqualTo(RouteType.HIGHWAY);
    }

    private UUID charge(User user, Car car, DataSource source, int hour, RouteType routeType) {
        ChargingEntry entry = ChargingEntry.builder()
                .loggedAt(LocalDateTime.of(2026, 9, 10, hour, 0)).kwhCharged(new BigDecimal("20.0")).routeType(routeType).build();
        return gateway.ingestCharging(new IngestCommand(user.getId(), car.getId(), source, IngestDoor.CONNECTOR_PUSH, List.of(entry)))
                .created().get(0).getId();
    }

    private RouteType routeType(UUID logId) {
        return evLogRepository.findById(logId).orElseThrow().getRouteType();
    }
}
