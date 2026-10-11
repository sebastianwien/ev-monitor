package com.evmonitor.application.codelogin;

import com.evmonitor.application.AuthResponse;
import com.evmonitor.domain.AuthProvider;
import com.evmonitor.domain.User;
import com.evmonitor.domain.exception.ValidationException;
import com.evmonitor.infrastructure.security.GoogleIdTokenVerifier;
import com.evmonitor.infrastructure.security.JwtService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleTokenAuthServiceTest {

    private final GoogleIdTokenVerifier verifier = mock(GoogleIdTokenVerifier.class);
    private final PasswordlessAccountService accounts = mock(PasswordlessAccountService.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final GoogleTokenAuthService service = new GoogleTokenAuthService(verifier, accounts, jwtService);
    private static final SignupAttribution FIRSTEV = new SignupAttribution("firstev", "tile", "volkswagen_id_3", "de");

    @Test
    void validTokenLogsInWithTheVerifiedAddress() {
        User user = User.createNewSsoUser("neu@gmail.com", "neu", AuthProvider.GOOGLE);
        when(verifier.verifiedEmail("id-token")).thenReturn(Optional.of("Neu@Gmail.com"));
        when(accounts.findOrCreateVerified("neu@gmail.com", FIRSTEV, AuthProvider.GOOGLE)).thenReturn(user);
        when(jwtService.generateToken(any(UserPrincipal.class))).thenReturn("jwt");

        AuthResponse response = service.login("id-token", FIRSTEV);

        assertThat(response.token()).isEqualTo("jwt");
        assertThat(response.email()).isEqualTo("neu@gmail.com");
    }

    @Test
    void invalidTokenIsRejectedWithoutTouchingAccounts() {
        when(verifier.verifiedEmail("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("bad", FIRSTEV))
                .isInstanceOf(ValidationException.class)
                .extracting("code").isEqualTo("INVALID_GOOGLE_TOKEN");
        verify(accounts, never()).findOrCreateVerified(any(), any(), any());
    }
}
