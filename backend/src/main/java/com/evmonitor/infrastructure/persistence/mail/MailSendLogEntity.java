package com.evmonitor.infrastructure.persistence.mail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;
import java.util.UUID;

/** Schema-Entity für die H2-Tests und als Spaltendoku; geschrieben per JDBC in {@link JdbcMailSendLog}. */
@Entity
@Table(name = "mail_send_log")
@Getter
@NoArgsConstructor
public class MailSendLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "mail_type", nullable = false, length = 60)
    private String mailType;
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "sent", nullable = false)
    private boolean sent;
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;
}
