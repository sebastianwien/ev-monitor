package com.evmonitor.infrastructure.web;

import com.evmonitor.application.waitlist.WaitlistService;
import com.evmonitor.domain.User;
import com.evmonitor.domain.WaitlistFeature;
import com.evmonitor.infrastructure.security.UserPrincipal;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WaitlistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WaitlistService waitlistService;

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
        when(waitlistService.status(eq(USER_ID), eq(WaitlistFeature.XPENG_AUTOSYNC)))
                .thenReturn(new WaitlistService.WaitlistStatus(true, LocalDateTime.of(2026, 9, 1, 10, 0)));

        mockMvc.perform(get("/api/waitlist/XPENG_AUTOSYNC").with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onWaitlist").value(true));
    }

    @Test
    void join_returnsOnWaitlist() throws Exception {
        when(waitlistService.join(eq(USER_ID), eq(WaitlistFeature.XPENG_AUTOSYNC)))
                .thenReturn(new WaitlistService.WaitlistStatus(true, LocalDateTime.of(2026, 9, 4, 12, 0)));

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
