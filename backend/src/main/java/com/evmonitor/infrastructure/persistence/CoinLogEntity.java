package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.CoinType;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "coin_log")
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

    public CoinLogEntity() {
    }

    public CoinLogEntity(UUID id, UUID userId, CoinType coinType, Integer amount,
                         String actionDescription, UUID sourceEntityId, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.coinType = coinType;
        this.amount = amount;
        this.actionDescription = actionDescription;
        this.sourceEntityId = sourceEntityId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public CoinType getCoinType() {
        return coinType;
    }

    public void setCoinType(CoinType coinType) {
        this.coinType = coinType;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public String getActionDescription() {
        return actionDescription;
    }

    public void setActionDescription(String actionDescription) {
        this.actionDescription = actionDescription;
    }

    public UUID getSourceEntityId() {
        return sourceEntityId;
    }

    public void setSourceEntityId(UUID sourceEntityId) {
        this.sourceEntityId = sourceEntityId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
