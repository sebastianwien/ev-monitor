package com.evmonitor.application.euda;

/** Fehlerklassen des VW-ID-Logins - der Controller entscheidet daran, was der Nutzer zu sehen bekommt. */
public class EudaAuthException extends RuntimeException {

    EudaAuthException(String message) {
        super(message);
    }

    /** E-Mail oder Passwort falsch - Nutzer muss neu eingeben. */
    public static class InvalidCredentials extends EudaAuthException {
        public InvalidCredentials() {
            super("E-Mail oder Passwort falsch");
        }
    }

    /** Die VW-ID verlangt eine manuelle Aktion im Browser (Terms, Consent, 2FA, Onboarding). */
    public static class InteractionRequired extends EudaAuthException {
        private final String reason;

        public InteractionRequired(String reason) {
            super("Bitte einmal im VW-Portal anmelden: " + reason);
            this.reason = reason;
        }

        public String reason() {
            return reason;
        }
    }

    /** 5xx, Timeout, Verbindungsfehler - spaeter erneut versuchen, kein Nutzerfehler. */
    public static class PortalUnavailable extends EudaAuthException {
        public PortalUnavailable(String message) {
            super(message);
        }
    }
}
