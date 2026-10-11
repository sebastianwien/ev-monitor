package com.evmonitor.application.codelogin;

import com.evmonitor.application.AuthResponse;
import com.evmonitor.domain.AuthProvider;
import com.evmonitor.domain.EmailAddress;
import com.evmonitor.domain.User;
import com.evmonitor.domain.exception.ValidationException;
import com.evmonitor.infrastructure.security.GoogleIdTokenVerifier;
import com.evmonitor.infrastructure.security.JwtService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Login with a Google ID token (One Tap in the gate). Google has verified the address, so an
 * existing account with that address is logged in, a new one is created like the mail-code path.
 */
@Service
@RequiredArgsConstructor
public class GoogleTokenAuthService {

    private final GoogleIdTokenVerifier verifier;
    private final PasswordlessAccountService accountService;
    private final JwtService jwtService;

    public AuthResponse login(String idToken, SignupAttribution attribution) {
        String email = verifier.verifiedEmail(idToken)
                .map(EmailAddress::normalize)
                .orElseThrow(() -> new ValidationException("INVALID_GOOGLE_TOKEN", "Google-Anmeldung ungültig"));
        User user = accountService.findOrCreateVerified(email, attribution, AuthProvider.GOOGLE);
        return AuthResponse.forUser(jwtService.generateToken(UserPrincipal.create(user)), user);
    }
}
