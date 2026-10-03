package com.evmonitor.application.voice;

import java.util.List;

/** Port Stufe 1: Audio zu Text. Audio wird nur durchgereicht, nie gespeichert oder geloggt. */
public interface SpeechTranscriber {

    /** @param biasTerms Einzelwoerter aus {@link BiasTermBuilder}, ohne Leerzeichen und Komma */
    Transcript transcribe(byte[] audio, String mimeType, List<String> biasTerms);
}
