package com.evmonitor.application.routetype;

import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.RouteType;
import com.evmonitor.domain.RouteTypeSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RouteTypeRecalcSchedulerTest {

    private final EvLogRepository repo = mock(EvLogRepository.class);
    private final RouteTypeDerivationService derivation = mock(RouteTypeDerivationService.class);
    private final RouteTypeRecalcScheduler scheduler = new RouteTypeRecalcScheduler(repo, derivation);

    @Test
    void failingCandidate_doesNotStopTheOthers() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        LocalDateTime since = LocalDateTime.of(2026, 10, 13, 0, 0);
        when(repo.findRouteTypeRecalcCandidateIds(since)).thenReturn(List.of(a, b, c));
        when(derivation.derive(a)).thenThrow(new IllegalStateException("kaputt"));
        when(derivation.derive(b)).thenReturn(Optional.of(new RouteTypeDerivationService.Derivation(RouteType.HIGHWAY, RouteTypeSource.TRIPS)));
        when(derivation.derive(c)).thenReturn(Optional.empty());

        RouteTypeRecalcScheduler.Result r = scheduler.run(since);

        assertThat(r).isEqualTo(new RouteTypeRecalcScheduler.Result(3, 1, 1));
        verify(derivation, times(3)).derive(any());
    }
}
