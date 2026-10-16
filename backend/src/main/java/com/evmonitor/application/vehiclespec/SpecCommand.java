package com.evmonitor.application.vehiclespec;

import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.VehicleSpecification;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Anlegen oder Bearbeiten einer Spec-Zeile im Admin-Tab. Marke ergibt sich aus dem Modell,
 * wltp_type ist immer COMBINED. Die fachlichen Regeln prüft {@link AdminVehicleSpecificationService}.
 */
@Builder(toBuilder = true)
public record SpecCommand(
        @NotNull(message = "Modell ist Pflicht.") CarBrand.CarModel model,
        @NotNull(message = "Brutto-kWh ist Pflicht.") @Positive @DecimalMax("500.0") BigDecimal batteryCapacityKwh,
        @NotNull(message = "Netto-kWh ist Pflicht.") @Positive @DecimalMax("500.0") BigDecimal netBatteryCapacityKwh,
        @NotNull(message = "WLTP-Reichweite ist Pflicht.") @Positive @DecimalMax("2000") BigDecimal officialRangeKm,
        @NotNull(message = "WLTP-Verbrauch ist Pflicht.") @Positive @DecimalMax("100") BigDecimal officialConsumptionKwhPer100km,
        @Size(max = 100) String variantName,
        @Size(max = 40) String trimLevel,
        LocalDate availableFrom,
        LocalDate availableTo,
        @Size(max = 500) String sourceUrl,
        VehicleSpecification.RatingSource ratingSource,
        BigDecimal dcMaxPowerKw,
        BigDecimal dcAvgPower1080Kw) {
}
