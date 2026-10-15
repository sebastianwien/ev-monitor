package com.evmonitor.application.vehiclespec;

import com.evmonitor.domain.vehiclespec.MissingVehicleRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Zeile der Arbeitsliste "Gemeldet" im Admin-Tab. Die Kontakt-Mail bleibt im Backend. */
public record MissingVehicleRequestResponse(UUID id, String freeText, String brand, String model, BigDecimal netKwh,
                                            Integer buildYear, LocalDateTime createdAt, boolean adminNotified,
                                            boolean contactAvailable) {

    public static MissingVehicleRequestResponse from(MissingVehicleRequest r) {
        return new MissingVehicleRequestResponse(r.getId(), r.getFreeText(), r.getBrand(), r.getModel(),
                r.getNetKwh(), r.getBuildYear(), r.getCreatedAt(), r.getAdminNotifiedAt() != null,
                r.getContactEmail() != null);
    }
}
