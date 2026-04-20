package com.evmonitor.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SmartcarWebhookRawLogRepository extends JpaRepository<SmartcarWebhookRawLog, UUID> {

    boolean existsByEventId(String eventId);

    Optional<SmartcarWebhookRawLog> findByEventId(String eventId);
}
