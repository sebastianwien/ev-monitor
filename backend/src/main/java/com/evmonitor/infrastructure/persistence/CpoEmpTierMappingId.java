package com.evmonitor.infrastructure.persistence;

import java.io.Serializable;
import java.util.Objects;

public class CpoEmpTierMappingId implements Serializable {
    private String empName;
    private String cpoName;

    public CpoEmpTierMappingId() {}

    public CpoEmpTierMappingId(String empName, String cpoName) {
        this.empName = empName;
        this.cpoName = cpoName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CpoEmpTierMappingId)) return false;
        CpoEmpTierMappingId that = (CpoEmpTierMappingId) o;
        return Objects.equals(empName, that.empName) && Objects.equals(cpoName, that.cpoName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(empName, cpoName);
    }
}
