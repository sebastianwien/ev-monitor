package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.CoinType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "coin_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CoinLogEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "coin_type", nullable = false, length = 30)
    private CoinType coinType;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "action_description", length = 500)
    private String actionDescription;

    @Column(name = "source_entity_id")
    private UUID sourceEntityId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
