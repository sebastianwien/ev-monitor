package com.evmonitor.domain;

/** Wie oft ein Nutzer in einer privaten Geohash-Zelle (6 Stellen, ~600 m) geladen hat. */
public record PrivateCellCount(String cell, long count) {}
