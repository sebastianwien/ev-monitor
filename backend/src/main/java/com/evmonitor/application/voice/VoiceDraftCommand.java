package com.evmonitor.application.voice;

import java.time.ZoneId;
import java.util.UUID;

/**
 * Eine Sprachaufnahme zum Auswerten. Audio lebt nur fuer diesen Aufruf.
 *
 * @param lat  Position fuer die Umkreissuche, nie gespeichert; null ohne Standortfreigabe
 * @param zone Zeitzone des Geraets - "gestern Abend" und der Monatsdeckel haengen daran
 */
public record VoiceDraftCommand(UUID userId, UUID carId, byte[] audio, String mimeType,
                                Double lat, Double lon, ZoneId zone) {}
