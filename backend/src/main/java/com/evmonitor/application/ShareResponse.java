package com.evmonitor.application;

/**
 * Antwort auf das Teilen einer Ladung.
 *
 * @param token Zufalls-Token der oeffentlichen URL
 * @param url   fertige absolute URL - der Client soll sie nicht selbst zusammensetzen
 *              muessen, sonst weicht sie zwischen Web, iOS und Android auseinander
 */
public record ShareResponse(String token, String url) {}
