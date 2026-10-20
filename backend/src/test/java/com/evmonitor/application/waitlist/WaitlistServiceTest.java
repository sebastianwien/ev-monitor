package com.evmonitor.application.waitlist;

import com.evmonitor.domain.UserRepository;
import com.evmonitor.domain.WaitlistFeature;
import com.evmonitor.infrastructure.persistence.waitlist.FeatureWaitlistEntry;
import com.evmonitor.infrastructure.persistence.waitlist.FeatureWaitlistRepository;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WaitlistServiceTest {

    @Mock FeatureWaitlistRepository repo;
    @Mock UserRepository userRepository;
    @InjectMocks WaitlistService service;

    private static final UUID USER = UUID.randomUUID();

    @Test
    void join_whenNotYetOnList_savesEntryAndReportsOnWaitlist() {
        when(repo.findByUserIdAndFeature(USER, WaitlistFeature.XPENG_AUTOSYNC)).thenReturn(Optional.empty());
        when(repo.save(any(FeatureWaitlistEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        var status = service.join(USER, WaitlistFeature.XPENG_AUTOSYNC);

        assertThat(status.onWaitlist()).isTrue();
        assertThat(status.since()).isNotNull();
        verify(repo).save(any(FeatureWaitlistEntry.class));
    }

    @Test
    void join_whenAlreadyOnList_isIdempotent_doesNotSaveAgain() {
        var existing = FeatureWaitlistEntry.builder()
                .userId(USER).feature(WaitlistFeature.XPENG_AUTOSYNC)
                .createdAt(LocalDateTime.of(2026, 9, 1, 10, 0)).build();
        when(repo.findByUserIdAndFeature(USER, WaitlistFeature.XPENG_AUTOSYNC)).thenReturn(Optional.of(existing));

        var status = service.join(USER, WaitlistFeature.XPENG_AUTOSYNC);

        assertThat(status.onWaitlist()).isTrue();
        assertThat(status.since()).isEqualTo(LocalDateTime.of(2026, 9, 1, 10, 0));
        verify(repo, never()).save(any());
    }

    @Test
    void status_reflectsPresence() {
        when(repo.findByUserIdAndFeature(USER, WaitlistFeature.XPENG_AUTOSYNC)).thenReturn(Optional.empty());
        assertThat(service.status(USER, WaitlistFeature.XPENG_AUTOSYNC).onWaitlist()).isFalse();

        var e = FeatureWaitlistEntry.builder().userId(USER).feature(WaitlistFeature.XPENG_AUTOSYNC)
                .createdAt(LocalDateTime.now()).build();
        when(repo.findByUserIdAndFeature(USER, WaitlistFeature.XPENG_AUTOSYNC)).thenReturn(Optional.of(e));
        assertThat(service.status(USER, WaitlistFeature.XPENG_AUTOSYNC).onWaitlist()).isTrue();
    }

    @Test
    void leave_delegatesDeletionToRepository() {
        service.leave(USER, WaitlistFeature.XPENG_AUTOSYNC);
        verify(repo).deleteByUserIdAndFeature(USER, WaitlistFeature.XPENG_AUTOSYNC);
    }

    @Test
    void adminEntries_listsWaitersInOrderWithEmail() {
        UUID second = UUID.randomUUID();
        FeatureWaitlistEntry first = FeatureWaitlistEntry.builder().userId(USER).feature(WaitlistFeature.POLESTAR_DATA_PORTAL)
                .createdAt(LocalDateTime.of(2026, 10, 20, 9, 0)).build();
        FeatureWaitlistEntry later = FeatureWaitlistEntry.builder().userId(second).feature(WaitlistFeature.POLESTAR_DATA_PORTAL)
                .createdAt(LocalDateTime.of(2026, 10, 21, 9, 0)).build();
        when(repo.findAllByFeatureOrderByCreatedAtAsc(WaitlistFeature.POLESTAR_DATA_PORTAL)).thenReturn(List.of(first, later));
        when(userRepository.findAllByIds(List.of(USER, second))).thenReturn(List.of(
                TestDataBuilder.createTestUserWithId(second, "zwei@ev-monitor.net", "h"),
                TestDataBuilder.createTestUserWithId(USER, "eins@ev-monitor.net", "h")));

        List<WaitlistService.AdminEntry> entries = service.adminEntries(WaitlistFeature.POLESTAR_DATA_PORTAL);

        assertEquals(2, entries.size());
        assertEquals("eins@ev-monitor.net", entries.get(0).email());
        assertEquals(LocalDateTime.of(2026, 10, 20, 9, 0), entries.get(0).since());
        assertEquals("zwei@ev-monitor.net", entries.get(1).email());
    }
}
