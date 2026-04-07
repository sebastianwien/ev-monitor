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

        // Optional — auto-generated from email prefix if absent
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
        String utmCampaign,

        @Size(max = 255, message = "Referrer source must be max 255 characters")
        String referrerSource,

        @Size(max = 10, message = "Registration locale must be max 10 characters")
        String registrationLocale,

        @Size(max = 2, message = "Country code must be max 2 characters")
        String country) {
}
