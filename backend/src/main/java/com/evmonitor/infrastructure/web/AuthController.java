package com.evmonitor.infrastructure.web;

import com.evmonitor.application.AuthResponse;
import com.evmonitor.application.AuthService;
import com.evmonitor.application.LoginRequest;
import com.evmonitor.application.RegisterRequest;
import com.evmonitor.application.RegisterResponse;
import com.evmonitor.application.codelogin.CodeVerifyRequest;
import com.evmonitor.application.codelogin.EmailCodeAuthService;
import com.evmonitor.application.codelogin.GoogleTokenAuthService;
import com.evmonitor.application.codelogin.SignupAttribution;
import com.evmonitor.domain.EmailAddress;
import com.evmonitor.infrastructure.security.RateLimitService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RateLimitService rateLimitService;
    private final EmailCodeAuthService emailCodeAuthService;
    private final GoogleTokenAuthService googleTokenAuthService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request,
                                      HttpServletRequest httpRequest) {
        if (!rateLimitService.tryConsumeRegister(clientIp(httpRequest))) {
            return tooManyRequests(600);
        }
        return ResponseEntity.ok(authService.register(request));
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

    /** Mails a login code. Same answer for known and unknown addresses. */
    @PostMapping("/code/request")
    public ResponseEntity<?> requestLoginCode(@Valid @RequestBody CodeRequest request,
                                              HttpServletRequest httpRequest) {
        if (!rateLimitService.tryConsumeCodeRequestIp(clientIp(httpRequest))) {
            return tooManyRequests(900);
        }
        // Volles Adress-Limit: gleiche Antwort, aber kein neuer Code. Sonst könnte jeder, der die Adresse
        // kennt, den gültigen Code des Inhabers immer wieder ersetzen und den Login so sperren.
        if (rateLimitService.tryConsumeCodeMail(EmailAddress.normalize(request.email()))) {
            emailCodeAuthService.requestCode(request.email(), request.locale());
        }
        return ResponseEntity.ok(Map.of("status", "SENT"));
    }

    /** Logs in with the mailed code; creates the account on first use. 400 INVALID_CODE otherwise. */
    @PostMapping("/code/verify")
    public ResponseEntity<?> verifyLoginCode(@Valid @RequestBody CodeVerifyBody request,
                                             HttpServletRequest httpRequest) {
        if (!rateLimitService.tryConsumeLogin(clientIp(httpRequest))) {
            return tooManyRequests(300);
        }
        return ResponseEntity.ok(emailCodeAuthService.verifyCode(
                new CodeVerifyRequest(request.email(), request.code(), request.attribution())));
    }

    /** Google One Tap: verifies the ID token, logs in or creates the account. 400 INVALID_GOOGLE_TOKEN otherwise. */
    @PostMapping("/google/token")
    public ResponseEntity<?> loginWithGoogleToken(@Valid @RequestBody GoogleTokenBody request,
                                                  HttpServletRequest httpRequest) {
        if (!rateLimitService.tryConsumeLogin(clientIp(httpRequest))) {
            return tooManyRequests(300);
        }
        return ResponseEntity.ok(googleTokenAuthService.login(request.idToken(), request.attribution()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(authService.refreshToken(principal));
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

    record CodeRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @Size(max = 10) String locale) {}

    record CodeVerifyBody(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Pattern(regexp = "\\s*\\d{6}\\s*") String code,
            @Size(max = 100) String utmSource,
            @Size(max = 100) String utmMedium,
            @Size(max = 100) String utmCampaign,
            @Size(max = 10) String locale) {
        SignupAttribution attribution() {
            return new SignupAttribution(utmSource, utmMedium, utmCampaign, locale);
        }
    }

    record GoogleTokenBody(
            @NotBlank @Size(max = 4096) String idToken,
            @Size(max = 100) String utmSource,
            @Size(max = 100) String utmMedium,
            @Size(max = 100) String utmCampaign,
            @Size(max = 10) String locale) {
        SignupAttribution attribution() {
            return new SignupAttribution(utmSource, utmMedium, utmCampaign, locale);
        }
    }

    record ResendVerificationRequest(
            @NotBlank @Email String email) {}

    record ForgotPasswordRequest(
            @NotBlank @Email String email) {}

    record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8) String newPassword) {}

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
