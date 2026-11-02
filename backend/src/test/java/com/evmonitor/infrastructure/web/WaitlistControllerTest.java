package com.evmonitor.infrastructure.web;

import com.evmonitor.application.waitlist.WaitlistService;
import com.evmonitor.domain.User;
import com.evmonitor.domain.WaitlistFeature;
import com.evmonitor.infrastructure.security.UserPrincipal;
import com.evmonitor.testutil.TestDataBuilder;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WaitlistControllerTest extends AbstractIntegrationTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private Authentication auth() {
        User user = TestDataBuilder.createTestUserWithId(USER_ID, "waitlist@ev-monitor.net", "hash");
        UserPrincipal principal = UserPrincipal.create(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    void status_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/waitlist/XPENG_AUTOSYNC"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void join_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/waitlist/XPENG_AUTOSYNC"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void status_returnsServiceResult() throws Exception {
        doReturn(new WaitlistService.WaitlistStatus(true, LocalDateTime.of(2026, 9, 1, 10, 0))).when(waitlistService).status(eq(USER_ID), eq(WaitlistFeature.XPENG_AUTOSYNC));

        mockMvc.perform(get("/api/waitlist/XPENG_AUTOSYNC").with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onWaitlist").value(true));
    }

    @Test
    void join_returnsOnWaitlist() throws Exception {
        doReturn(new WaitlistService.WaitlistStatus(true, LocalDateTime.of(2026, 9, 4, 12, 0))).when(waitlistService).join(eq(USER_ID), eq(WaitlistFeature.XPENG_AUTOSYNC));

        mockMvc.perform(post("/api/waitlist/XPENG_AUTOSYNC").with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onWaitlist").value(true));
    }

    @Test
    void leave_returns204AndDelegates() throws Exception {
        mockMvc.perform(delete("/api/waitlist/XPENG_AUTOSYNC").with(authentication(auth())))
                .andExpect(status().isNoContent());
        verify(waitlistService).leave(eq(USER_ID), eq(WaitlistFeature.XPENG_AUTOSYNC));
    }

    @Test
    void unknownFeature_returns400() throws Exception {
        mockMvc.perform(get("/api/waitlist/NOT_A_FEATURE").with(authentication(auth())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}
