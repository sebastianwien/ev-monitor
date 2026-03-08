package com.evmonitor.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Pattern(regexp = "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$",
                 message = "Email address must contain a valid domain (e.g. name@example.com)")
        String email,

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_]{3,20}$", message = "Username must contain only alphanumeric characters and underscores")
        String username,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        String referralCode,

        @Size(max = 100, message = "UTM source must be max 100 characters")
        String utmSource,

        @Size(max = 100, message = "UTM medium must be max 100 characters")
        String utmMedium,

        @Size(max = 100, message = "UTM campaign must be max 100 characters")
        String utmCampaign) {
}
