package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * toEntity() baut die UserEntity bei jedem Save neu auf. Ein Nutzer, der sich aus den
 * Bestenlisten ausgetragen hat, darf dadurch nicht wieder sichtbar werden.
 */
class UserLeaderboardVisibleSaveTest extends AbstractIntegrationTest {

    @Test
    void optOut_survivesUserSave() {
        User user = createAndSaveUser("lb-visible-" + UUID.randomUUID().toString().substring(0, 8) + "@ev-monitor.net");
        userRepository.setLeaderboardVisible(user.getId(), false);

        userRepository.save(userRepository.findById(user.getId()).orElseThrow());

        assertThat(userRepository.isLeaderboardVisible(user.getId())).isFalse();
    }
}
