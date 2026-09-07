package com.evmonitor.infrastructure.external.xpeng;

/**
 * Pro-User-Autorisierung aus der XPeng-Data-Access-Notice. Diese drei Werte erhaelt der
 * autorisierte Fahrzeughalter von XPeng und werden von EVMonitor pro Fahrzeug gespeichert.
 */
public record XpengUserCredentials(String openId, String accessToken, String scopeCode) {
}
