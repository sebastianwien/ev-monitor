package com.evmonitor.application.vehiclespec;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.evmonitor.domain.vehiclespec.MissingVehicleRequest;

import java.math.BigDecimal;

/** Eingabe des Dialogs "Fehlt etwas?". Marke und Modell kommen aus der laufenden Auswahl, wenn vorhanden. */
public record MissingVehicleRequestCommand(
        @NotBlank @Size(max = 150) String freeText,
        @NotBlank @Size(max = 254) String contactEmail,
        @Size(max = 60) String brand,
        @Size(max = 80) String model,
        @DecimalMin("1") @DecimalMax("300") BigDecimal netKwh,
        @Min(1990) @Max(2035) Integer buildYear) {

    /** Unverändert in den Domain-Entwurf; Bereinigung und Prüfung macht der Service vorher. */
    public MissingVehicleRequest.Draft toDraft() {
        return new MissingVehicleRequest.Draft(contactEmail, freeText, brand, model, netKwh, buildYear);
    }
}
