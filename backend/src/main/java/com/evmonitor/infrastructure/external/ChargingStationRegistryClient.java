package com.evmonitor.infrastructure.external;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
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
     * Eine gemeldete Ladeeinrichtung mit allem, was das Register Brauchbares fuehrt.
     *
     * <p>"Nennleistung Ladeeinrichtung" ist die Summe aller Stecker und wird bewusst nicht
     * uebernommen; gebraucht wird die Leistung je Ladeart aus den Steckerfeldern.
     *
     * @param operator     handelsrechtlicher Betreibername, im Register immer gesetzt
     * @param brand        Anzeigename fuer die Karte; bei 56 % der Schnellladeeinrichtungen leer
     * @param maxAcKw      hoechste AC-Steckerleistung, kann fehlen
     * @param maxDcKw      hoechste DC-Steckerleistung, kann fehlen
     * @param chargePoints Anzahl Ladepunkte der Einrichtung, kann fehlen
     * @param registerId   Ladeeinrichtungs-ID der Bundesnetzagentur, kann fehlen
     * @param plugTypes    Kurzformen der Stecker (Typ 2, CCS, CHAdeMO, Schuko), ohne Doppelte
     */
    public record Station(String operator, String brand, Double latitude, Double longitude,
                          Double maxAcKw, Double maxDcKw, Integer chargePoints,
                          Integer registerId, String street, String houseNumber, String postalCode, String city,
                          List<String> plugTypes, LocalDate commissionedOn, String siteLabel,
                          String payment, String openingHours) {

        /** Nur Namen - reicht fuer den Ladenetz-Abgleich. */
        public Station(String operator, String brand) {
            this(operator, brand, null, null, null, false, null);
        }

        /** Position, Gesamtleistung und Ladeart - fuer Tests ohne Steckerdetails. */
        public Station(String operator, String brand, Double latitude, Double longitude,
                       Double powerKw, boolean fastCharging, Integer chargePoints) {
            this(operator, brand, latitude, longitude, fastCharging ? null : powerKw, fastCharging ? powerKw : null,
                    chargePoints, null, null, null, null, null, List.of(), null, null, null, null);
        }

        public boolean hasPosition() {
            return latitude != null && longitude != null;
        }

        public boolean fastCharging() {
            return maxDcKw != null;
        }
    }

    private static final String FAST_CHARGING = "Schnellladeeinrichtung";
    private static final String OUT_OF_SERVICE = "Außer Betrieb";
    private static final int MAX_PLUGS = 6;
    private static final String OUT_FIELDS = "Ladeeinrichtungs_ID,Betreiber,Anzeigename__Karte_,Status,Breitengrad,Längengrad,"
            + "Nennleistung_Ladeeinrichtung__kW_,Art_der_Ladeeinrichtung,Anzahl_Ladepunkte,Inbetriebnahmedatum,"
            + "Straße,Hausnummer,Postleitzahl,Ort,Standortbezeichnung,Bezahlsysteme,Öffnungszeiten,"
            + java.util.stream.IntStream.rangeClosed(1, MAX_PLUGS)
                    .mapToObj(i -> "Steckertypen" + i + ",Nennleistung_Stecker" + i)
                    .collect(java.util.stream.Collectors.joining(","));

    /**
     * Alle gemeldeten Ladeeinrichtungen im Umkreis, jede einzeln - ein Standort mit sechs
     * Saeulen kommt sechsmal. Ohne Filter auf die Ladeart.
     *
     * @return die gefundenen Standorte - eine leere Liste heisst "dort steht nichts",
     *         ein leeres Optional heisst "das Register hat nicht geantwortet"
     */
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
                .queryParam("outFields", OUT_FIELDS)
                .queryParam("returnGeometry", false)
                .queryParam("resultRecordCount", MAX_RECORDS)
                .queryParam("f", "json")
                .build()
                .encode()
                .toUri();
        return fetchStations(uri);
    }

    /** Hoechstens so viele Suchbegriffe; mehr ist kein Suchtext mehr, sondern Rauschen. */
    static final int MAX_SEARCH_TOKENS = 5;

    /**
     * Textsuche: jeder Begriff muss in Betreiber, Anzeigename, Ort oder Strasse vorkommen,
     * Zahlen als Postleitzahl-Praefix oder Hausnummer. So findet "EnBW Lichtenau" die Saeule
     * ohne Strassenangabe und "Fuchsgraben Lichtenau" dieselbe ohne Betreiber.
     *
     * @return wie {@link #findStationsNearby}; ohne brauchbaren Begriff eine leere Liste ohne Anfrage
     */
    public Optional<List<Station>> searchStations(String query) {
        if (!enabled) {
            return Optional.empty();
        }
        String where = whereClause(query);
        if (where == null) {
            return Optional.of(List.of());
        }
        URI uri = UriComponentsBuilder.fromUriString(QUERY_URL)
                .queryParam("where", where)
                .queryParam("outFields", OUT_FIELDS)
                .queryParam("returnGeometry", false)
                .queryParam("resultRecordCount", MAX_RECORDS)
                .queryParam("f", "json")
                .build()
                .encode()
                .toUri();
        return fetchStations(uri);
    }

    /**
     * Where-Klausel aus Suchbegriffen. Der Text geht an einen fremden Dienst: Hochkommas werden
     * verdoppelt, die Wildcards des Dienstes ({@code %} und {@code _}) entfernt, Begriffe unter
     * zwei Zeichen ignoriert. Null, wenn kein Begriff uebrig bleibt.
     */
    static String whereClause(String query) {
        if (query == null) return null;
        List<String> clauses = new java.util.ArrayList<>();
        for (String raw : query.split("[\\s,]+")) {
            String token = raw.replaceAll("[%_]", "").replace("'", "''").trim();
            if (token.length() < 2) continue;
            if (token.chars().allMatch(Character::isDigit)) {
                clauses.add("(Postleitzahl LIKE '" + token + "%' OR Hausnummer = '" + token + "')");
            } else {
                String t = "'%" + token.toUpperCase(java.util.Locale.ROOT) + "%'";
                clauses.add("(UPPER(Betreiber) LIKE " + t + " OR UPPER(Anzeigename__Karte_) LIKE " + t
                        + " OR UPPER(Ort) LIKE " + t + " OR UPPER(Straße) LIKE " + t + ")");
            }
            if (clauses.size() == MAX_SEARCH_TOKENS) break;
        }
        return clauses.isEmpty() ? null : String.join(" AND ", clauses);
    }

    @SuppressWarnings("unchecked")
    private Optional<List<Station>> fetchStations(URI uri) {
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
                    .filter(a -> !OUT_OF_SERVICE.equalsIgnoreCase(text(a.get("Status"))))
                    .map(ChargingStationRegistryClient::toStation)
                    .filter(s -> s.operator() != null)
                    .toList());
        } catch (Exception e) {
            log.debug("Ladesaeulenregister nicht erreichbar: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static Station toStation(Map<String, Object> a) {
        Plugs plugs = plugs(a);
        Double total = number(a.get("Nennleistung_Ladeeinrichtung__kW_"));
        boolean fast = FAST_CHARGING.equalsIgnoreCase(text(a.get("Art_der_Ladeeinrichtung")));
        // Ohne Steckerangaben bleibt nur die Gesamtleistung, zugeordnet nach Art der Einrichtung.
        Double ac = plugs.maxAc != null ? plugs.maxAc : (fast ? null : total);
        Double dc = plugs.maxDc != null ? plugs.maxDc : (fast ? total : null);
        return new Station(
                text(a.get("Betreiber")),
                text(a.get("Anzeigename__Karte_")),
                number(a.get("Breitengrad")),
                number(a.get("Längengrad")),
                ac, dc,
                integer(a.get("Anzahl_Ladepunkte")),
                integer(a.get("Ladeeinrichtungs_ID")),
                text(a.get("Straße")), text(a.get("Hausnummer")), text(a.get("Postleitzahl")), text(a.get("Ort")),
                plugs.types,
                date(a.get("Inbetriebnahmedatum")),
                text(a.get("Standortbezeichnung")),
                text(a.get("Bezahlsysteme")),
                text(a.get("Öffnungszeiten")));
    }

    private record Plugs(Double maxAc, Double maxDc, List<String> types) {}

    /**
     * Steckerfelder 1-6: Typ und Leistung, mehrere Stecker je Feld mit ";" getrennt
     * ("DC ... (CCS); DC CHAdeMO" / "50; 50"). Leistung je Ladeart = Maximum ueber alle Stecker.
     */
    private static Plugs plugs(Map<String, Object> a) {
        Double maxAc = null, maxDc = null;
        java.util.LinkedHashSet<String> types = new java.util.LinkedHashSet<>();
        for (int i = 1; i <= MAX_PLUGS; i++) {
            String typeField = text(a.get("Steckertypen" + i));
            if (typeField == null) continue;
            String[] typeParts = typeField.split(";");
            String[] kwParts = text(a.get("Nennleistung_Stecker" + i)) == null
                    ? new String[0] : text(a.get("Nennleistung_Stecker" + i)).split(";");
            for (int j = 0; j < typeParts.length; j++) {
                String type = typeParts[j].trim();
                boolean dc = type.toUpperCase(java.util.Locale.ROOT).startsWith("DC");
                Double kw = j < kwParts.length ? parseKw(kwParts[j]) : null;
                if (kw != null) {
                    if (dc) maxDc = maxDc == null ? kw : Math.max(maxDc, kw);
                    else maxAc = maxAc == null ? kw : Math.max(maxAc, kw);
                }
                String shortType = shortPlugType(type);
                if (shortType != null) types.add(shortType);
            }
        }
        return new Plugs(maxAc, maxDc, List.copyOf(types));
    }

    private static Double parseKw(String s) {
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** "DC Fahrzeugkupplung Typ Combo 2 (CCS)" -> "CCS", "AC Typ 2 Fahrzeugkupplung" -> "Typ 2". */
    static String shortPlugType(String type) {
        String t = type.toLowerCase(java.util.Locale.ROOT);
        if (t.contains("ccs") || t.contains("combo")) return "CCS";
        if (t.contains("chademo")) return "CHAdeMO";
        if (t.contains("typ 2") || t.contains("type 2")) return "Typ 2";
        if (t.contains("typ 1") || t.contains("type 1")) return "Typ 1";
        if (t.contains("schuko")) return "Schuko";
        if (t.contains("cee")) return "CEE";
        if (t.contains("tesla")) return "Tesla";
        return t.isBlank() ? null : type;
    }

    private static LocalDate date(Object value) {
        return value instanceof Number n
                ? java.time.Instant.ofEpochMilli(n.longValue()).atZone(java.time.ZoneId.of("Europe/Berlin")).toLocalDate()
                : null;
    }

    private static Double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }

    private static Integer integer(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }

    /**
     * Das Register liefert Namen mit Leerzeichen am Rand, geschuetzten Leerzeichen und
     * Doppelspaces - alles auf einfache Leerzeichen zusammengezogen.
     */
    private static String text(Object value) {
        if (!(value instanceof String s)) return null;
        String cleaned = s.replaceAll("[\\s\\u00a0]+", " ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
