package com.evmonitor.infrastructure.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "cpo_emp_tier_mapping")
@IdClass(CpoEmpTierMappingId.class)
public class CpoEmpTierMappingEntity {

    @Id
    @Column(name = "emp_name", nullable = false)
    private String empName;

    @Id
    @Column(name = "cpo_name", nullable = false)
    private String cpoName;

    @Column(name = "price_tier", nullable = false)
    private String priceTier;

    public CpoEmpTierMappingEntity() {}

    public String getEmpName() { return empName; }
    public void setEmpName(String empName) { this.empName = empName; }

    public String getCpoName() { return cpoName; }
    public void setCpoName(String cpoName) { this.cpoName = cpoName; }

    public String getPriceTier() { return priceTier; }
    public void setPriceTier(String priceTier) { this.priceTier = priceTier; }
}
