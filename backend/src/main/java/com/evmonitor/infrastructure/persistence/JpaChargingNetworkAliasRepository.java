package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaChargingNetworkAliasRepository extends JpaRepository<ChargingNetworkAliasEntity, String> {
}
