package com.evmonitor.application.user;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(
        @NotBlank(message = "Passwort zur Bestätigung erforderlich")
        String password
) {
}
