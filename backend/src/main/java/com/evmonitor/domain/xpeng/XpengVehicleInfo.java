package com.evmonitor.domain.xpeng;

public record XpengVehicleInfo(
        String vin,
        String model,
        String color,
        String productionDate,
        String otaVersion
) {}
