package com.evmonitor.infrastructure.persistence.xpeng;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "xpeng_received_mail")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class XpengReceivedMail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "connection_id", nullable = false)
    private UUID connectionId;

    @Column(name = "message_id", nullable = false, unique = true)
    private String messageId;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "attachment_name")
    private String attachmentName;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
