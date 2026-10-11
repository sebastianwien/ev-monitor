package com.evmonitor.application.codelogin;

import com.evmonitor.application.AuthResponse;
import com.evmonitor.domain.AuthProvider;
import com.evmonitor.domain.EmailLoginCode;
import com.evmonitor.domain.EmailLoginCodeRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.exception.ValidationException;
import com.evmonitor.infrastructure.email.EmailService;
import com.evmonitor.infrastructure.security.JwtService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailCodeAuthServiceTest {

    private final InMemoryCodes codes = new InMemoryCodes();
    private final EmailService emailService = mock(EmailService.class);
    private final PasswordlessAccountService accounts = mock(PasswordlessAccountService.class);
    private final JwtService jwtService = mock(JwtService.class);
    private EmailCodeAuthService service;

    private static final SignupAttribution FIRSTEV = new SignupAttribution("firstev", "tile", "volkswagen_id_3", "de");

    @BeforeEach
    void setUp() {
        service = new EmailCodeAuthService(codes, new BCryptPasswordEncoder(4), emailService, accounts, jwtService);
        when(jwtService.generateToken(any(UserPrincipal.class))).thenReturn("jwt");
    }

    @Test
    void requestStoresOnlyAHashAndMailsSixDigits() {
        String code = requestAndCaptureCode("Neu@Example.com");

        assertThat(code).matches("\\d{6}");
        assertThat(codes.rows).hasSize(1);
        EmailLoginCode row = codes.rows.get(0);
        assertThat(row.email()).isEqualTo("neu@example.com");
        assertThat(row.codeHash()).doesNotContain(code);
        assertThat(row.expiresAt()).isBetween(LocalDateTime.now().plusMinutes(9), LocalDateTime.now().plusMinutes(11));
    }

    @Test
    void newRequestReplacesTheOldCode() {
        requestAndCaptureCode("a@example.com");
        requestAndCaptureCode("a@example.com");

        assertThat(codes.rows).hasSize(1);
    }

    @Test
    void correctCodeLogsInAndConsumesTheCode() {
        String code = requestAndCaptureCode("neu@example.com");
        User user = User.createVerifiedLocalUser("neu@example.com", "neu", null);
        when(accounts.findOrCreateVerified("neu@example.com", FIRSTEV, AuthProvider.LOCAL)).thenReturn(user);

        AuthResponse response = service.verifyCode(new CodeVerifyRequest("NEU@example.com ", code, FIRSTEV));

        assertThat(response.token()).isEqualTo("jwt");
        assertThat(response.email()).isEqualTo("neu@example.com");
        assertThat(codes.rows).isEmpty();
    }

    @Test
    void wrongCodeCountsAnAttempt() {
        String code = requestAndCaptureCode("a@example.com");
        String wrong = code.equals("000000") ? "111111" : "000000";

        assertThatThrownBy(() -> service.verifyCode(new CodeVerifyRequest("a@example.com", wrong, FIRSTEV)))
                .isInstanceOf(ValidationException.class);

        assertThat(codes.rows.get(0).attempts()).isEqualTo(1);
        verify(accounts, never()).findOrCreateVerified(any(), any(), any());
    }

    @Test
    void fifthWrongAttemptBurnsTheCode() {
        String code = requestAndCaptureCode("a@example.com");
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.verifyCode(new CodeVerifyRequest("a@example.com", wrong, FIRSTEV)))
                    .isInstanceOf(ValidationException.class);
        }

        assertThat(codes.rows).isEmpty();
        assertThatThrownBy(() -> service.verifyCode(new CodeVerifyRequest("a@example.com", code, FIRSTEV)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void expiredCodeIsRejected() {
        String code = requestAndCaptureCode("a@example.com");
        EmailLoginCode row = codes.rows.get(0);
        codes.rows.set(0, new EmailLoginCode(row.id(), row.email(), row.codeHash(),
                LocalDateTime.now().minusSeconds(1), 0, row.createdAt()));

        assertThatThrownBy(() -> service.verifyCode(new CodeVerifyRequest("a@example.com", code, FIRSTEV)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void unknownAddressGetsTheSameErrorAsAWrongCode() {
        assertThatThrownBy(() -> service.verifyCode(new CodeVerifyRequest("nie@example.com", "123456", FIRSTEV)))
                .isInstanceOf(ValidationException.class)
                .extracting("code").isEqualTo("INVALID_CODE");
    }

    private String requestAndCaptureCode(String email) {
        service.requestCode(email, "de");
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailService, org.mockito.Mockito.atLeastOnce()).sendLoginCodeEmail(eq(email.trim().toLowerCase()), code.capture(), eq("de"));
        return code.getValue();
    }

    /** Minimal in-memory stand-in for the code table. */
    private static class InMemoryCodes implements EmailLoginCodeRepository {
        final List<EmailLoginCode> rows = new ArrayList<>();

        @Override public EmailLoginCode save(EmailLoginCode code) {
            rows.removeIf(r -> r.id().equals(code.id()));
            rows.add(code);
            return code;
        }

        @Override public Optional<EmailLoginCode> findLatestByEmail(String email) {
            return rows.stream().filter(r -> r.email().equals(email))
                    .max(Comparator.comparing(EmailLoginCode::createdAt));
        }

        @Override public void deleteByEmail(String email) { rows.removeIf(r -> r.email().equals(email)); }

        @Override public void deleteExpired(LocalDateTime now) { rows.removeIf(r -> r.expiresAt().isBefore(now)); }
    }
}
