package com.evmonitor.application.voice;

/** Port Stufe 2: Transkript plus Kontext zu Ladevorgangs-Feldern. */
public interface LogDraftExtractor {

    Extraction extract(String transcript, ExtractionContext context);
}
