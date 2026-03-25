package com.evmonitor.application.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UserChargingProviderRequest(
        @NotBlank @Size(max = 100) String providerName,
        BigDecimal acPricePerKwh,
        BigDecimal dcPricePerKwh,
        BigDecimal monthlyFeeEur,
        BigDecimal sessionFeeEur,
        @NotNull @PastOrPresent LocalDate activeFrom
) {
}
