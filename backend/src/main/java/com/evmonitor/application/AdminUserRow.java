package com.evmonitor.application;

public record AdminUserRow(
        String email,
        String createdAt,
        String username,
        String models,
        String utmSource,
        String referrerSource,
        long evlogCount,
        String dataSources
) {}
