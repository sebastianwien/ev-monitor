package com.evmonitor.application.voice;

import com.evmonitor.application.user.UserChargingProviderResponse;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Alles, was ein Sprachlog-Request ueber den Nutzer weiss: Ort-Kandidaten und Tarife (Index =
 * Listenposition), Betreiber der letzten Logs, letzter Tachostand, Datum und Fahrzeug fuer den Prompt.
 */
public record VoiceContext(List<PlaceCandidate> candidates, List<UserChargingProviderResponse> tariffs,
                           List<String> recentOperators, Integer lastOdometerKm, String today, String vehicle) {

    public ExtractionContext toExtractionContext() {
        return new ExtractionContext(today, vehicle, lastOdometerKm,
                IntStream.range(0, candidates.size())
                        .mapToObj(i -> new ExtractionContext.Candidate(i, candidates.get(i).promptKind(), candidates.get(i).name()))
                        .toList(),
                IntStream.range(0, tariffs.size())
                        .mapToObj(i -> new ExtractionContext.Tariff(i, tariffName(tariffs.get(i))))
                        .toList());
    }

    public List<String> biasTerms() {
        return BiasTermBuilder.build(candidates.stream().map(PlaceCandidate::name).toList(),
                tariffs.stream().map(VoiceContext::tariffName).toList(), recentOperators);
    }

    /** Name wie im Wizard, eigene Bezeichnung in Klammern dahinter - die Klammer faellt fuer die Bias-Liste weg. */
    static String tariffName(UserChargingProviderResponse t) {
        String label = t.label();
        if (label == null || label.isBlank() || label.trim().equalsIgnoreCase(t.providerName())) return t.providerName();
        return t.providerName() + " (" + label.trim() + ")";
    }
}
