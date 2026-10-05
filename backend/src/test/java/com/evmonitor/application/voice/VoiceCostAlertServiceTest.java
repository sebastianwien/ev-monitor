package com.evmonitor.application.voice;

import com.evmonitor.application.AdminAlertService;
import com.evmonitor.infrastructure.persistence.voice.VoiceDraftRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Mail an den Betreiber, sobald die Sprachlog-Kosten des laufenden Monats eine weitere 10-USD-Stufe ueberschreiten. */
class VoiceCostAlertServiceTest {

    private final VoiceDraftRepository repository = mock(VoiceDraftRepository.class);
    private final AdminAlertService alerts = mock(AdminAlertService.class);
    private final VoiceCostAlertService service = new VoiceCostAlertService(repository, alerts, BigDecimal.TEN);

    private void totals(String month, String total) {
        when(repository.sumCostUsd()).thenReturn(new BigDecimal(total));
        when(repository.sumCostUsdSince(any())).thenReturn(new BigDecimal(month));
    }

    @Test
    void crossingFirstStep_sendsAlert() {
        totals("10.004", "52.100");
        service.afterRecorded(new BigDecimal("0.005"));
        verify(alerts).sendVoiceCostAlert(eq(new BigDecimal("10.004")), eq(new BigDecimal("52.100")), eq(BigDecimal.TEN));
    }

    @Test
    void withinSameStep_sendsNothing() {
        totals("10.600", "90.000");
        service.afterRecorded(new BigDecimal("0.001"));
        verifyNoInteractions(alerts);
    }

    @Test
    void crossingLaterStep_reportsThatStep() {
        totals("20.002", "99.000");
        service.afterRecorded(new BigDecimal("0.004"));
        verify(alerts).sendVoiceCostAlert(any(), any(), eq(new BigDecimal("20")));
    }

    @Test
    void totalCrossesStep_butMonthDoesNot_sendsNothing() {
        totals("3.002", "30.001");
        service.afterRecorded(new BigDecimal("0.005"));
        verifyNoInteractions(alerts);
    }

    @Test
    void zeroCost_sendsNothing() {
        totals("10.000", "10.000");
        service.afterRecorded(BigDecimal.ZERO);
        verifyNoInteractions(alerts);
    }

    @Test
    void repositoryFailure_isSwallowed() {
        when(repository.sumCostUsdSince(any())).thenThrow(new RuntimeException("db down"));
        service.afterRecorded(new BigDecimal("0.005"));
        verifyNoInteractions(alerts);
    }
}
