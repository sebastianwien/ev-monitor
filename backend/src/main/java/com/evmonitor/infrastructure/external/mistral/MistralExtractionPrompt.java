package com.evmonitor.infrastructure.external.mistral;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prompt v3 und JSON-Schema der Extraktion, uebernommen aus dem Eval vom 03.10.2026
 * ({@code sprachlog/eval/bias/extract.py}). Aenderungen am Prompt nur mit erneuter Messung.
 */
final class MistralExtractionPrompt {

    static final String SYSTEM = """
Du extrahierst aus dem Transkript einer Sprachaufnahme die Daten eines Ladevorgangs eines Elektroautos.
Das Transkript stammt aus automatischer Spracherkennung: Wörter können falsch, englisch oder lautmalerisch geschrieben sein (z. B. "EONETI" für IONITY, "Prozen"/"pros" für Prozent). Interpretiere phonetisch.
Regeln:
- Nie raten. Was nicht gesagt wurde, ist null. Felder, bei denen du interpretieren musstest, kommen in "uncertain".
- Ort: Wähle placeIndex aus der Kandidatenliste, wenn der gesprochene Betreiber oder Ort klar zu einem Kandidaten passt (auch bei Fehlschreibung). "zuhause", "daheim", "eigene Wallbox" = Kandidat mit kind home. Passt kein Kandidat: placeIndex null, placeKind other. spokenOperator nur mit einem Namen, der im Transkript tatsächlich vorkommt, nie aus der Kandidatenliste übernehmen. Wird kein Ort genannt: placeIndex, placeKind und spokenOperator null.
- Ladekarte/Tarif ("mit der X-Karte", "über X bezahlt"): tariffIndex nur, wenn der gesprochene Name klanglich eindeutig zu genau einem Tarif passt; im Zweifel null und "tariffIndex" in uncertain. Ein Tarif ist kein Ort.
- kwhCharged = an der Säule/Wallbox geladene Energie. kwhAtVehicle nur, wenn ausdrücklich die im Auto angekommene Energie genannt wird.
- Tacho/Kilometerstand: ganze km. Tausendertrennzeichen können als Komma oder Punkt erscheinen ("31,207" = 31207 km). Der Wert liegt typischerweise wenig über dem letzten Tachostand.
- SoC "von A auf B" = socBefore A, socAfter B. Nur eine Zahl ("jetzt 85 Prozent", "auf 80") = socAfter.
- Euro: "16 Euro 52" = 16.52; "kostenlos" = 0. Preis pro kWh nur in pricePerKwh.
- Dauer in Minuten ("1 Stunde 5 Minuten" = 65).
- kWh/Kilowattstunden ist Energie, kW/Kilowatt ist Leistung. maxChargingPowerKw nur, wenn ausdrücklich eine Leistung genannt wird ("Spitze 150 kW", "mit 11 kW", "Ladeleistung"). Eine gesprochene Zahl landet nie in zwei Feldern.
- chargingType DC bei Schnelllader/HPC/Supercharger/über 22 kW, AC bei Wallbox/zuhause/11 kW; sonst null.
- routeType: Stadt/Stadtverkehr=CITY, Autobahn=HIGHWAY, gemischt=COMBINED, sonst null. tireType analog (Winterreifen, Ganzjahres, Sommer).
- loggedAt nur bei Zeitangabe ("gestern Abend" = gestern 19:00) als lokale Zeit "YYYY-MM-DDTHH:MM", sonst null.""";

    private MistralExtractionPrompt() {}

    static Map<String, Object> schema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("placeIndex", integer());
        props.put("placeKind", oneOf("home", "station", "other"));
        props.put("spokenOperator", nullable("string"));
        props.put("tariffIndex", integer());
        props.put("kwhCharged", number());
        props.put("kwhAtVehicle", number());
        props.put("socBefore", integer());
        props.put("socAfter", integer());
        props.put("odometerKm", integer());
        props.put("costEur", number());
        props.put("pricePerKwh", number());
        props.put("loggedAt", nullable("string"));
        props.put("chargeDurationMinutes", integer());
        props.put("maxChargingPowerKw", number());
        props.put("chargingType", oneOf("AC", "DC"));
        props.put("routeType", oneOf("CITY", "COMBINED", "HIGHWAY"));
        props.put("tireType", oneOf("SUMMER", "ALL_YEAR", "WINTER"));
        props.put("uncertain", Map.of("type", "array", "items", Map.of("type", "string")));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", props);
        schema.put("required", List.copyOf(props.keySet()));
        return schema;
    }

    private static Map<String, Object> integer() {
        return nullable("integer");
    }

    private static Map<String, Object> number() {
        return nullable("number");
    }

    private static Map<String, Object> nullable(String type) {
        return Map.of("type", List.of(type, "null"));
    }

    private static Map<String, Object> oneOf(String... values) {
        List<Object> allowed = new java.util.ArrayList<>(List.of((Object[]) values));
        allowed.add(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", List.of("string", "null"));
        m.put("enum", allowed);
        return m;
    }
}
