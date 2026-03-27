package com.evmonitor.application;

public record AdminUserGrowthRow(
        String day,
        long newUsers,
        long cumulativeUsers
) {}
