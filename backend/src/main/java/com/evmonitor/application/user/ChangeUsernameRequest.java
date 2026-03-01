package com.evmonitor.application.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangeUsernameRequest(
        @NotBlank(message = "Username darf nicht leer sein")
        @Size(min = 3, max = 20, message = "Username muss zwischen 3 und 20 Zeichen lang sein")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username darf nur Buchstaben, Zahlen und Unterstriche enthalten")
        String newUsername
) {
}
