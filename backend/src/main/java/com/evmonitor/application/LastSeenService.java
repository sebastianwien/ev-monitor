package com.evmonitor.application;

import com.evmonitor.infrastructure.persistence.JpaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class LastSeenService {

    private final Set<UUID> activeUsers = ConcurrentHashMap.newKeySet();
    private final JpaUserRepository jpaUserRepository;

    public void recordActivity(UUID userId) {
        activeUsers.add(userId);
    }

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void flush() {
        if (activeUsers.isEmpty()) return;
        List<UUID> ids = new ArrayList<>(activeUsers);
        activeUsers.removeAll(ids);
        jpaUserRepository.batchUpdateLastSeen(ids, LocalDateTime.now());
    }
}
