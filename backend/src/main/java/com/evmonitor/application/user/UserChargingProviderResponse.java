package com.evmonitor.application.user;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UserChargingProviderResponse(
        UUID id,
        String providerName,
        String label,
        BigDecimal acPricePerKwh,
        BigDecimal dcPricePerKwh,
        BigDecimal monthlyFeeEur,
        BigDecimal sessionFeeEur,
        LocalDate activeFrom,
        LocalDate activeUntil
) {
}
