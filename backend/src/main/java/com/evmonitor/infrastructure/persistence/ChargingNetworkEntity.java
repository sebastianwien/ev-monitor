package com.evmonitor.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "charging_networks")
@Getter
@Setter
@NoArgsConstructor
public class ChargingNetworkEntity {

    @Id
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "country_code")
    private String countryCode;
}
