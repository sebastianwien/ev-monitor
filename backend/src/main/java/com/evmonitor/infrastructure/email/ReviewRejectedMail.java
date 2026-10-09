package com.evmonitor.infrastructure.email;

import java.util.UUID;

/** Inhalt der Ablehnungs-Mail. {@code carId} null, wenn das Auto nicht mehr existiert. */
public record ReviewRejectedMail(
        String toEmail,
        String username,
        String locale,
        String modelName,
        String reason,
        String note,
        UUID carId) {
}
