package com.evmonitor.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class BatterySohEntry {

    private final UUID id;
    private final UUID carId;
    private final BigDecimal sohPercent;
    private final LocalDate recordedAt;
    private final LocalDateTime createdAt;
}
