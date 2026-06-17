package com.evmonitor.application;

/**
 * Most-used public charging provider this month. The provider name is the user's
 * charging card (EMP) name when set, falling back to the station operator (CPO) name.
 */
public record TopProviderResult(String providerName, long count) {
}
