package com.evmonitor.domain.route;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RouteSketchRepository extends JpaRepository<RouteSketch, RouteSketch.Key> {

    Optional<RouteSketch> findByStartGeohashAndEndGeohash(String startGeohash, String endGeohash);
}
