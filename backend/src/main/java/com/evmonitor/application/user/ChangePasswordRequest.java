package com.evmonitor.application.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Aktuelles Passwort darf nicht leer sein")
        String currentPassword,

        @NotBlank(message = "Neues Passwort darf nicht leer sein")
        @Size(min = 8, message = "Passwort muss mindestens 8 Zeichen lang sein")
        String newPassword
) {
}
