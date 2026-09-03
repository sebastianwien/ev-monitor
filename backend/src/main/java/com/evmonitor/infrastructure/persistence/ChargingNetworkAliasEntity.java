package com.evmonitor.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Abbildung einer Firmierung aus dem Ladesaeulenregister auf ein Ladenetz in
 * {@link ChargingNetworkEntity}. Der Alias steht klein geschrieben in der Tabelle.
 */
@Entity
@Table(name = "charging_network_alias")
@Getter
@Setter
@NoArgsConstructor
public class ChargingNetworkAliasEntity {

    @Id
    @Column(name = "alias", nullable = false)
    private String alias;

    @Column(name = "network_name", nullable = false)
    private String networkName;
}
