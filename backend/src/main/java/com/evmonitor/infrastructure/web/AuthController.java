package com.evmonitor.infrastructure.web;

import com.evmonitor.application.AuthResponse;
import com.evmonitor.application.AuthService;
import com.evmonitor.application.LoginRequest;
import com.evmonitor.application.RegisterRequest;
import com.evmonitor.application.RegisterResponse;
import com.evmonitor.infrastructure.security.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RateLimitService rateLimitService;

    public AuthController(AuthService authService, RateLimitService rateLimitService) {
        this.authService = authService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request,
                                      HttpServletRequest httpRequest) {
        if (!rateLimitService.tryConsumeRegister(clientIp(httpRequest))) {
            return tooManyRequests(600);
        }
        // Enrich with GeoIP country from nginx header if not already set in request
        RegisterRequest enriched = request;
        if (request.country() == null || request.country().isBlank()) {
            String geoCountry = httpRequest.getHeader("X-Country-Code");
            if (geoCountry != null && geoCountry.length() == 2) {
                enriched = new RegisterRequest(
                        request.email(), request.username(), request.password(),
                        request.referralCode(), request.utmSource(), request.utmMedium(),
                        request.utmCampaign(), request.referrerSource(),
                        request.registrationLocale(), geoCountry.toUpperCase());
            }
        }
        return ResponseEntity.ok(authService.register(enriched));
    }

    @PostMapping("/demo-login")
    public ResponseEntity<?> demoLogin(HttpServletRequest httpRequest) {
        if (!rateLimitService.tryConsumeDemoLogin(clientIp(httpRequest))) {
            return tooManyRequests(600);
        }
        return ResponseEntity.ok(authService.demoLogin());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request,
                                   HttpServletRequest httpRequest) {
        if (!rateLimitService.tryConsumeLogin(clientIp(httpRequest))) {
            return tooManyRequests(300);
        }
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<AuthResponse> verifyEmail(@RequestParam String token) {
        return ResponseEntity.ok(authService.verifyEmail(token));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, String>> resendVerification(@RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.email());
        return ResponseEntity.ok(Map.of("message", "Falls diese E-Mail-Adresse registriert und noch nicht verifiziert ist, wurde eine neue E-Mail verschickt."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                                               HttpServletRequest httpRequest) {
        if (!rateLimitService.tryConsumeForgotPassword(clientIp(httpRequest))) {
            return tooManyRequests(600);
        }
        authService.forgotPassword(request.email());
        return ResponseEntity.ok(Map.of("message", "Falls diese E-Mail-Adresse registriert ist, wurde ein Reset-Link verschickt."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(Map.of("message", "Passwort erfolgreich geändert."));
    }

    record ResendVerificationRequest(
            @NotBlank @Email String email) {}

    record ForgotPasswordRequest(
            @NotBlank @Email String email) {}

    record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @jakarta.validation.constraints.Size(min = 8) String newPassword) {}

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Reads the real client IP, respecting X-Forwarded-For set by nginx. */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static ResponseEntity<Map<String, String>> tooManyRequests(int retryAfterSeconds) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", String.valueOf(retryAfterSeconds));
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .headers(headers)
                .body(Map.of("error", "Too many requests. Please try again later."));
    }
}
