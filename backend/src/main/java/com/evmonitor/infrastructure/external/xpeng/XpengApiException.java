package com.evmonitor.infrastructure.external.xpeng;

/**
 * Fehler beim Aufruf der XPeng Open API - entweder Transport (HTTP) oder ein
 * fachlicher Fehlercode ({@code code != 0}) aus der Antwort.
 *
 * <p>Bekannte Codes laut Integration Guide:
 * <ul>
 *   <li>12061001 - Request-Parameterfehler</li>
 *   <li>12061004 - Signaturfehler</li>
 *   <li>4004001  - accessToken abgelaufen</li>
 *   <li>12064019 - keine Autorisierung fuer diesen User</li>
 *   <li>12064024 - Download-Limit ueberschritten (max 5/24h)</li>
 * </ul>
 */
public class XpengApiException extends RuntimeException {

    public static final int TOKEN_EXPIRED = 4004001;
    public static final int RATE_LIMIT_EXCEEDED = 12064024;
    public static final int NO_AUTHORIZATION = 12064019;
    public static final int SIGN_ERROR = 12061004;
    public static final int PARAM_ERROR = 12061001;

    /** Sentinel fuer Transport-/HTTP-Fehler ohne fachlichen API-Code. */
    public static final int NO_CODE = -1;

    private final int code;

    public XpengApiException(int code, String message) {
        super(message);
        this.code = code;
    }

    public XpengApiException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public boolean isTokenExpired() {
        return code == TOKEN_EXPIRED;
    }

    public boolean isRateLimited() {
        return code == RATE_LIMIT_EXCEEDED;
    }
}
