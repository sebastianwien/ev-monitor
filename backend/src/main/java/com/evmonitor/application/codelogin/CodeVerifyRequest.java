package com.evmonitor.application.codelogin;

/** Attribution only matters when the address has no account yet. */
public record CodeVerifyRequest(String email, String code, SignupAttribution attribution) {}
