package com.evmonitor.infrastructure.web;

import com.evmonitor.application.voice.VoiceLogService;
import com.evmonitor.application.voice.VoiceQuotaService;
import com.evmonitor.domain.User;
import com.evmonitor.infrastructure.security.RateLimitService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Notschalter VOICE_LOG_ADMIN_ONLY=true: Nicht-Admins sehen den Sprachlog nicht (404), ohne Neustart der Freigabe-Logik im Frontend. */
class VoiceLogControllerSwitchTest {

    private final VoiceQuotaService quotaService = mock(VoiceQuotaService.class);
    private final VoiceLogController adminOnly = new VoiceLogController(
            mock(VoiceLogService.class), quotaService, mock(RateLimitService.class), true);

    private static UsernamePasswordAuthenticationToken as(String role) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(user.getRole()).thenReturn(role);
        UserPrincipal principal = mock(UserPrincipal.class);
        when(principal.getUser()).thenReturn(user);
        return new UsernamePasswordAuthenticationToken(principal, null);
    }

    @Test
    void withTheSwitchOnNonAdminsGetNotFound() {
        assertThat(adminOnly.voiceQuota("Europe/Berlin", as("USER")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(quotaService, never()).quota(any(), any());
    }
}
