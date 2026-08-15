package com.evmonitor.application;

/**
 * Eine geteilte Ladekurve wurde zurueckgezogen.
 *
 * Sorgt dafuer, dass das gecachte Vorschaubild sofort verschwindet - sonst
 * wuerde es weiter ausgeliefert, waehrend die Seite selbst schon tot ist.
 */
public record ShareRevokedEvent(String token) {}
