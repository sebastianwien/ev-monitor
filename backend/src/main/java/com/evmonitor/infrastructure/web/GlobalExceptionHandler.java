package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.exception.AuthException;
import com.evmonitor.domain.exception.ConflictException;
import com.evmonitor.domain.exception.DomainException;
import com.evmonitor.domain.exception.ForbiddenException;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.domain.exception.ValidationException;
import com.evmonitor.infrastructure.github.GitHubIssueService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final GitHubIssueService gitHubIssueService;

    // ── Domain exception hierarchy ───────────────────────────────────────────

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(ForbiddenException ex) {
        return body(HttpStatus.FORBIDDEN, ex);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(ConflictException ex) {
        return body(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, String>> handleDomainValidation(ValidationException ex) {
        return body(HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, String>> handleAuth(AuthException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "TOKEN_EXPIRED" -> HttpStatus.GONE;
            case "EMAIL_NOT_VERIFIED" -> HttpStatus.FORBIDDEN;
            case "RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_REQUEST; // INVALID_TOKEN and friends
        };
        return body(status, ex);
    }

    // Fallback: Legacy IllegalArgumentException aus noch nicht migrierten Services.
    // Sollte mittelfristig leer laufen – jede neue Ausnahme gehört in die DomainException-Hierarchie.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        String code = ex.getMessage();
        HttpStatus status = switch (code) {
            case "TOKEN_EXPIRED" -> HttpStatus.GONE;
            case "INVALID_TOKEN" -> HttpStatus.BAD_REQUEST;
            case "EMAIL_NOT_VERIFIED" -> HttpStatus.FORBIDDEN;
            case "RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(Map.of("code", code, "message", friendlyMessage(code)));
    }

    // ── Spring / Validation errors ───────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(e -> e.getDefaultMessage())
                .orElse("Ungültige Eingabe.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("code", "VALIDATION_ERROR", "message", message));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("code", "INVALID_CREDENTIALS", "message", "Ungültige Anmeldedaten."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", "FORBIDDEN", "message", "Zugriff verweigert."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "code", "INVALID_JSON",
                "message", "Ungültiges JSON-Format. Bitte überprüfe deine Anfrage."
        ));
    }

    // Spring MVC 4xx - kein Alert, kein Log-Spam
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "NOT_FOUND", "message", "Resource not found."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("code", "INVALID_PARAMETER",
                "message", "Ungültiger Parameterwert: " + ex.getName()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("code", "MISSING_PARAMETER",
                "message", "Pflichtparameter fehlt: " + ex.getParameterName()));
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncNotUsable(AsyncRequestNotUsableException ex) {
        // Client hat die Verbindung getrennt - kein Logging, kein GitHub Issue
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);

        String errorKey = ex.getClass().getName();
        String subject = "🚨 [EV Monitor BE] " + ex.getClass().getSimpleName();
        gitHubIssueService.createIssue(errorKey, subject, buildGitHubIssueBody(ex, request));

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "code", "INTERNAL_ERROR",
                "message", "Ein interner Fehler ist aufgetreten."
        ));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, String>> body(HttpStatus status, DomainException ex) {
        // DomainException liefert bereits eine user-facing (deutsche) Message.
        // Für reine Error-Codes wie "EMAIL_NOT_VERIFIED" mappen wir zusätzlich auf einen
        // freundlichen Text, weil Tests und Frontend bisher darauf setzen.
        String code = ex.getCode();
        String rawMessage = ex.getMessage();
        String message = friendlyMessage(rawMessage);
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
    }

    private String buildGitHubIssueBody(Exception ex, HttpServletRequest request) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        String stackTrace = sw.toString();
        if (stackTrace.length() > 5000) {
            stackTrace = stackTrace.substring(0, 5000) + "\n... (truncated)";
        }

        return """
                ## Backend Error

                | Field | Value |
                |-------|-------|
                | **Time** | `%s` |
                | **Request** | `%s %s` |
                | **Exception** | `%s` |
                | **Message** | `%s` |

                ## Stacktrace

                ```
                %s
                ```

                ---
                *Auto-generated by EV Monitor error handler. Label `auto-fix` triggers automated analysis.*
                """.formatted(
                Instant.now(),
                request.getMethod(),
                request.getRequestURI(),
                ex.getClass().getName(),
                ex.getMessage(),
                stackTrace
        );
    }

    private String friendlyMessage(String code) {
        return switch (code) {
            case "TOKEN_EXPIRED" -> "Der Bestätigungs-Link ist abgelaufen. Bitte fordere einen neuen an.";
            case "INVALID_TOKEN" -> "Ungültiger Bestätigungs-Link.";
            case "EMAIL_NOT_VERIFIED" -> "Bitte bestätige zuerst deine E-Mail-Adresse.";
            case "RATE_LIMITED" -> "Bitte warte kurz, bevor du erneut eine E-Mail anforderst.";
            case "Email is already in use." -> "Diese E-Mail-Adresse ist bereits registriert.";
            case "Username is already taken." -> "Dieser Username ist bereits vergeben.";
            default -> code;
        };
    }
}
