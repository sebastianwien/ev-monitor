package com.evmonitor.application;

public record PlausibleTrafficRow(
        String date,
        int visitors,
        int pageviews
) {}
