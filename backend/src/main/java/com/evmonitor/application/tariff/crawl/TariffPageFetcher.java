package com.evmonitor.application.tariff.crawl;

/** Lädt eine Tarifseite für den Crawler. Wirft nie, das Ergebnis sagt, was schiefging. */
public interface TariffPageFetcher {

    Fetched fetch(String url);

    enum Outcome { OK, HTTP_ERROR, ROBOTS, BLOCKED_URL, UNREACHABLE, TOO_LARGE, NOT_HTML }

    /** {@code page} nur bei OK; {@code httpStatus} 0, wenn keine Antwort kam. */
    record Fetched(Outcome outcome, int httpStatus, PageText page, String error) {

        public static Fetched ok(int status, PageText page) {
            return new Fetched(Outcome.OK, status, page, null);
        }

        public static Fetched failed(Outcome outcome, int status, String error) {
            return new Fetched(outcome, status, null, error);
        }
    }
}
