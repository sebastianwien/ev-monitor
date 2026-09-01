package com.evmonitor.infrastructure.external;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fragt die nach Ladesaeulenverordnung gemeldeten Ladepunkte im Umkreis eines Punktes ab.
 *
 * <p>Quelle ist der offene ArcGIS-Dienst mit dem Datensatz "Ladesaeulen in Deutschland"
 * der Bundesnetzagentur (CC BY 4.0, monatliche Aktualisierung, Stand 07.2026:
 * 115.234 Ladeeinrichtungen). Er antwortet ohne Token. Der offizielle Endpunkt der
 * Bundesnetzagentur selbst verlangt einen Token und ist bewusst nicht angebunden.
 *
 * <p>Wir spiegeln den Bestand nicht: er aendert sich monatlich, und gebraucht wird er nur
 * in dem Moment, in dem jemand eine oeffentliche Ladung speichert. Gegen die Last schuetzt
 * der Cache in {@link com.evmonitor.application.NearbyCpoService}.
 *
 * <p>Uebertragen wird ausschliesslich der Mittelpunkt einer Geohash-Zelle, ohne Nutzerbezug
 * und ohne Zeitstempel. Der Dienst erfaehrt damit nicht, wo ein Nutzer wirklich stand.
 *
 * <p>Der Dienst ist fremd und hat kein zugesichertes Rate Limit. Ein Ausfall wird als leeres
 * {@link Optional} gemeldet und ist damit von der Antwort "hier steht nichts" unterscheidbar -
 * sonst wuerde ein kurzer Ausfall im Cache des Aufrufers festfrieren.
 */
@Component
@Slf4j
public class ChargingStationRegistryClient {

    private static final String QUERY_URL =
            "https://services2.arcgis.com/jUpNdisbWqRpMo35/arcgis/rest/services"
                    + "/Ladesaeulen_in_Deutschland/FeatureServer/0/query";

    /** Grenze des Dienstes pro Anfrage. Im Umkreis von wenigen hundert Metern nie erreicht. */
    private static final int MAX_RECORDS = 200;

    private final RestTemplate restTemplate;
    private final boolean enabled;

    public ChargingStationRegistryClient(RestTemplate restTemplate,
                                         @Value("${charging-station-registry.enabled:true}") boolean enabled) {
        this.restTemplate = restTemplate;
        this.enabled = enabled;
    }

    /**
     * Ein gemeldeter Ladestandort.
     *
     * @param operator handelsrechtlicher Betreibername, im Register immer gesetzt
     * @param brand    Anzeigename fuer die Karte; bei 56 % der Schnellladeeinrichtungen leer
     */
    public record Station(String operator, String brand) {}

    /**
     * Alle gemeldeten Ladestandorte im Umkreis. Ohne Filter auf die Ladeart: was nicht zu
     * einem bekannten Ladenetz gehoert, faellt ohnehin beim Namensabgleich heraus.
     *
     * @return die gefundenen Standorte - eine leere Liste heisst "dort steht nichts",
     *         ein leeres Optional heisst "das Register hat nicht geantwortet"
     */
    @SuppressWarnings("unchecked")
    public Optional<List<Station>> findStationsNearby(double lat, double lon, int radiusMeters) {
        if (!enabled) {
            return Optional.empty();
        }

        URI uri = UriComponentsBuilder.fromUriString(QUERY_URL)
                .queryParam("geometry", "{\"x\":" + lon + ",\"y\":" + lat + ",\"spatialReference\":{\"wkid\":4326}}")
                .queryParam("geometryType", "esriGeometryPoint")
                .queryParam("inSR", 4326)
                .queryParam("distance", radiusMeters)
                .queryParam("units", "esriSRUnit_Meter")
                .queryParam("spatialRel", "esriSpatialRelIntersects")
                .queryParam("outFields", "Betreiber,Anzeigename__Karte_")
                .queryParam("returnDistinctValues", true)
                .queryParam("returnGeometry", false)
                .queryParam("resultRecordCount", MAX_RECORDS)
                .queryParam("f", "json")
                .build()
                .encode()
                .toUri();

        try {
            Map<String, Object> response = restTemplate.getForObject(uri, Map.class);
            if (response == null || !(response.get("features") instanceof List<?> features)) {
                log.debug("Ladesaeulenregister: unerwartete Antwort, keine Vorschlaege");
                return Optional.empty();
            }
            return Optional.of(features.stream()
                    .map(f -> f instanceof Map<?, ?> m ? m.get("attributes") : null)
                    .filter(Map.class::isInstance)
                    .map(a -> (Map<String, Object>) a)
                    .map(a -> new Station(text(a.get("Betreiber")), text(a.get("Anzeigename__Karte_"))))
                    .filter(s -> s.operator() != null)
                    .toList());
        } catch (Exception e) {
            log.debug("Ladesaeulenregister nicht erreichbar: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Das Register liefert Namen mit Leerzeichen am Rand und Zeilenumbruechen in Nachbarfeldern. */
    private static String text(Object value) {
        if (!(value instanceof String s)) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
