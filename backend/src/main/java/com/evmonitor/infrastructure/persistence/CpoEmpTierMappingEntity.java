package com.evmonitor.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cpo_emp_tier_mapping")
@IdClass(CpoEmpTierMappingId.class)
@Getter
@Setter
@NoArgsConstructor
public class CpoEmpTierMappingEntity {

    @Id
    @Column(name = "emp_name", nullable = false)
    private String empName;

    @Id
    @Column(name = "cpo_name", nullable = false)
    private String cpoName;

    @Column(name = "price_tier", nullable = false)
    private String priceTier;
}
