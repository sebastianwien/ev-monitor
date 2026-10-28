package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface JpaEmpAliasRepository extends JpaRepository<EmpAliasEntity, String> {

    /** {@code alias} klein und ohne Rand-Leerraum. */
    @Query("SELECT a.empName FROM EmpAliasEntity a WHERE a.alias = :alias")
    Optional<String> findEmpNameByAlias(@Param("alias") String alias);
}
