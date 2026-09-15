package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface JpaEmpRepository extends JpaRepository<EmpEntity, String> {

    @Query("SELECT e.name FROM EmpEntity e ORDER BY e.name")
    List<String> findAllNamesSorted();
}
