package com.evmonitor.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GoeConnectionRepository extends JpaRepository<GoeConnection, UUID> {

    List<GoeConnection> findAllByUserId(UUID userId);

    Optional<GoeConnection> findBySerial(String serial);

    List<GoeConnection> findAllByActiveTrue();

    boolean existsBySerial(String serial);

    void deleteByIdAndUserId(UUID id, UUID userId);
}
