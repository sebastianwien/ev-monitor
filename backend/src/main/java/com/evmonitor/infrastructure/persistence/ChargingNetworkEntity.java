package com.evmonitor.infrastructure.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "charging_networks")
public class ChargingNetworkEntity {

    @Id
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "country_code")
    private String countryCode;

    public ChargingNetworkEntity() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
}
