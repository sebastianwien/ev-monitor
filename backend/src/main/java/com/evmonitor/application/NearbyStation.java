package com.evmonitor.application;

/**
 * Ein Ladestandort in der Naehe, als Vorschlag im Log-Formular.
 *
 * @param name           kanonischer Ladenetz-Name, sonst der Rohname aus dem Register
 * @param known          ob der Name einem bekannten Ladenetz zugeordnet werden konnte
 * @param distanceMeters Entfernung zum Mittelpunkt der Geohash-Zelle, nicht zum Nutzer
 * @param maxPowerKw     hoechste Nennleistung am Standort, kann fehlen
 * @param fastCharging   ob mindestens eine Schnellladeeinrichtung (DC) dort steht
 * @param chargePoints   Ladepunkte am Standort insgesamt
 * @param geohash        Zelle (7 Stellen) der naechstgelegenen Saeule dieses Betreibers
 */
public record NearbyStation(String name, boolean known, int distanceMeters,
                            Double maxPowerKw, boolean fastCharging, int chargePoints,
                            String geohash) {}
