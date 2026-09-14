package com.evmonitor.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ladekarten-Anbieter (E-Mobility Provider) aus dem Katalog, siehe V180. */
@Entity
@Table(name = "emp")
@Getter
@Setter
@NoArgsConstructor
public class EmpEntity {

    @Id
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "country_code", length = 2)
    private String countryCode;
}
