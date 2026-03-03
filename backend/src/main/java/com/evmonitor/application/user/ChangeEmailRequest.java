package com.evmonitor.application.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangeEmailRequest(
        @NotBlank(message = "Email darf nicht leer sein")
        @Email(message = "Ungültige Email-Adresse")
        @Pattern(regexp = "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$",
                 message = "Email-Adresse muss eine gültige Domain enthalten (z.B. name@example.com)")
        String newEmail
) {
}
