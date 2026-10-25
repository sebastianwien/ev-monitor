package com.evmonitor.application.tariff;

import com.evmonitor.domain.ChargingType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Anlegen oder Ändern eines gepflegten Tarifs im Admin-Tab "Ladekarten". Grenzen sind weit
 * genug für echte Tarife und eng genug, dass ein Tippfehler auffällt. Die fachlichen Regeln
 * (Katalog, Betreiber oder Tier, Zeitraum) prüft {@link AdminChargingTariffService}.
 */
public record TariffCommand(
        @NotBlank(message = "Anbieter ist Pflicht.") @Size(max = 100) String empName,
        @Size(max = 100) String tariffVariant,
        @Size(max = 100) String cpoName,
        @Pattern(regexp = "LOW|STANDARD|HIGH", message = "Tier ist LOW, STANDARD oder HIGH.") String priceTier,
        @NotNull(message = "Ladeart ist Pflicht.") ChargingType chargingType,
        @NotNull(message = "Preis ist Pflicht.") @DecimalMin("0.01") @DecimalMax("2.00") BigDecimal pricePerKwh,
        @DecimalMin("0") @DecimalMax("50") BigDecimal sessionFeeEur,
        @DecimalMin("0") @DecimalMax("100") BigDecimal monthlyFeeEur,
        @DecimalMin("0") @DecimalMax("5") BigDecimal blockingFeePerMin,
        @Min(0) @Max(1440) Integer blockingFeeAfterMin,
        boolean dynamicPricing,
        @NotNull(message = "Gültig ab ist Pflicht.") LocalDate validFrom,
        LocalDate validUntil,
        // Nur Web-Links: die Quelle wird im Admin-Tab als Link gezeigt.
        @Size(max = 500) @Pattern(regexp = "https?://\\S+", message = "Quelle muss ein http(s)-Link sein.") String sourceUrl) {
}
