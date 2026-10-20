package com.evmonitor.application.routetype;

import com.evmonitor.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RouteTypeBackfillJobTest {

    private final EvLogRepository repo = mock(EvLogRepository.class);
    private final RouteTypeDerivationService derivation = mock(RouteTypeDerivationService.class);
    private final RouteTypeBackfillJob job = new RouteTypeBackfillJob(repo, derivation);

    private static EvLog log(DataSource source) {
        return log(source, null, null);
    }

    private static EvLog log(DataSource source, RouteType routeType, RouteTypeSource routeTypeSource) {
        return EvLog.builder().id(UUID.randomUUID()).carId(UUID.randomUUID()).kwhCharged(new BigDecimal("20"))
                .loggedAt(LocalDateTime.of(2026, 3, 1, 10, 0)).dataSource(source)
                .routeType(routeType).routeTypeSource(routeTypeSource).build();
    }

    @Test
    void dryRun_countsBySourceAndResult_butWritesNothing() {
        EvLog goe = log(DataSource.WALLBOX_GOE);
        EvLog tesla = log(DataSource.TESLA_LIVE);
        EvLog none = log(DataSource.USER_LOGGED);
        when(repo.findRouteTypeBackfillCandidateIds()).thenReturn(List.of(goe.getId(), tesla.getId(), none.getId()));
        for (EvLog l : List.of(goe, tesla, none)) when(repo.findById(l.getId())).thenReturn(Optional.of(l));
        when(derivation.compute(goe)).thenReturn(Optional.of(new RouteTypeDerivationService.Derivation(RouteType.HIGHWAY, RouteTypeSource.TRIPS)));
        when(derivation.compute(tesla)).thenReturn(Optional.of(new RouteTypeDerivationService.Derivation(RouteType.HIGHWAY, RouteTypeSource.HEURISTIC)));
        when(derivation.compute(none)).thenReturn(Optional.empty());

        RouteTypeBackfillSummary s = job.run(true);

        assertThat(s.dryRun()).isTrue();
        assertThat(s.candidates()).isEqualTo(3);
        assertThat(s.changed()).isEqualTo(2);
        assertThat(s.bySource()).isEqualTo(Map.of("WALLBOX_GOE -> HIGHWAY/TRIPS", 1, "TESLA_LIVE -> HIGHWAY/HEURISTIC", 1));
        verify(repo, never()).updateRouteType(any(), any(), any());
    }

    @Test
    void sameValueAsStored_isNotCounted_andNotWritten() {
        EvLog tessie = log(DataSource.TESSIE, RouteType.HIGHWAY, RouteTypeSource.TRIPS);
        when(repo.findRouteTypeBackfillCandidateIds()).thenReturn(List.of(tessie.getId()));
        when(repo.findById(tessie.getId())).thenReturn(Optional.of(tessie));
        when(derivation.compute(tessie)).thenReturn(Optional.of(new RouteTypeDerivationService.Derivation(RouteType.HIGHWAY, RouteTypeSource.TRIPS)));

        RouteTypeBackfillSummary s = job.run(false);

        assertThat(s.changed()).isZero();
        verify(repo, never()).updateRouteType(any(), any(), any());
    }

    @Test
    void realRun_writesAndCountsErrors() {
        EvLog a = log(DataSource.WALLBOX_GOE);
        EvLog b = log(DataSource.TESLA_LIVE);
        when(repo.findRouteTypeBackfillCandidateIds()).thenReturn(List.of(a.getId(), b.getId()));
        when(repo.findById(a.getId())).thenReturn(Optional.of(a));
        when(repo.findById(b.getId())).thenReturn(Optional.of(b));
        when(derivation.compute(a)).thenReturn(Optional.of(new RouteTypeDerivationService.Derivation(RouteType.CITY, RouteTypeSource.TRIPS)));
        when(derivation.compute(b)).thenThrow(new IllegalStateException("kaputt"));

        RouteTypeBackfillSummary s = job.run(false);

        assertThat(s.dryRun()).isFalse();
        assertThat(s.changed()).isEqualTo(1);
        assertThat(s.errors()).isEqualTo(1);
        assertThat(s.bySource()).containsEntry("WALLBOX_GOE -> CITY/TRIPS", 1);
        verify(repo).updateRouteType(a.getId(), RouteType.CITY, RouteTypeSource.TRIPS);
    }

    @Test
    void runAsync_releasesTheLock_andKeepsTheLastSummary() {
        when(repo.findRouteTypeBackfillCandidateIds()).thenReturn(List.of());

        assertThat(job.tryStart()).isTrue();
        assertThat(job.tryStart()).isFalse();
        job.runAsync(true);

        assertThat(job.status().running()).isFalse();
        assertThat(job.status().last().candidates()).isZero();
        assertThat(job.tryStart()).isTrue();
    }
}
