package com.evmonitor.application.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ChangeEmailRequest(
        @NotBlank(message = "Email darf nicht leer sein")
        @Email(message = "Ungültige Email-Adresse")
        String newEmail
) {
}
