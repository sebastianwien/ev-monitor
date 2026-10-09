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
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Login by a 6-digit mail code, without leaving the page. Works for new addresses (account is
 * created on first successful code) and existing ones. Same answer whether an address exists or
 * not; the code is stored as BCrypt hash, checked with PasswordEncoder.matches and never logged.
 */
@Service
@RequiredArgsConstructor
public class EmailCodeAuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailLoginCodeRepository codeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final PasswordlessAccountService accountService;
    private final JwtService jwtService;

    @Transactional
    public void requestCode(String rawEmail, String locale) {
        String email = normalize(rawEmail);
        codeRepository.deleteExpired(LocalDateTime.now());
        codeRepository.deleteByEmail(email);
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        codeRepository.save(EmailLoginCode.create(email, passwordEncoder.encode(code)));
        emailService.sendLoginCodeEmail(email, code, locale);
    }

    /** @throws ValidationException INVALID_CODE for unknown, wrong, expired or exhausted codes alike */
    @Transactional(noRollbackFor = ValidationException.class)
    public AuthResponse verifyCode(CodeVerifyRequest request) {
        String email = normalize(request.email());
        EmailLoginCode stored = codeRepository.findLatestByEmail(email)
                .filter(c -> !c.isExpired() && !c.isExhausted())
                .orElseThrow(EmailCodeAuthService::invalidCode);

        if (request.code() == null || !passwordEncoder.matches(request.code().trim(), stored.codeHash())) {
            EmailLoginCode failed = stored.withFailedAttempt();
            if (failed.isExhausted()) codeRepository.deleteByEmail(email);
            else codeRepository.save(failed);
            throw invalidCode();
        }

        codeRepository.deleteByEmail(email);
        User user = accountService.findOrCreateVerified(email, request.attribution(), AuthProvider.LOCAL);
        return AuthResponse.forUser(jwtService.generateToken(UserPrincipal.create(user)), user);
    }

    public static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static ValidationException invalidCode() {
        return new ValidationException("INVALID_CODE", "Code ungültig oder abgelaufen");
    }
}
