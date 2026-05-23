package com.evmonitor.infrastructure.persistence.xpeng;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface XpengReceivedMailRepository extends JpaRepository<XpengReceivedMail, UUID> {
    boolean existsByMessageId(String messageId);
    List<XpengReceivedMail> findByConnectionIdOrderByReceivedAtDesc(UUID connectionId);
}
