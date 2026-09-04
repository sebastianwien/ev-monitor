package com.evmonitor.application.waitlist;

import com.evmonitor.domain.WaitlistFeature;
import com.evmonitor.infrastructure.persistence.waitlist.FeatureWaitlistEntry;
import com.evmonitor.infrastructure.persistence.waitlist.FeatureWaitlistRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WaitlistServiceTest {

    @Mock FeatureWaitlistRepository repo;
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
}
