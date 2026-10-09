package com.evmonitor.infrastructure.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * Checks Google ID tokens from One Tap / Sign in with Google: signature against Google's JWKS,
 * issuer, audience (our client ID), expiry and email_verified. Without a configured client ID
 * nothing is accepted.
 */
@Component
public class GoogleIdTokenVerifier {

    private static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private final JwtDecoder decoder;

    @Autowired
    public GoogleIdTokenVerifier(@Value("${GOOGLE_CLIENT_ID:}") String clientId) {
        this(clientId.isBlank() ? null : withGoogleRules(NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI).build(), clientId));
    }

    GoogleIdTokenVerifier(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    static GoogleIdTokenVerifier disabled() {
        return new GoogleIdTokenVerifier((JwtDecoder) null);
    }

    static NimbusJwtDecoder withGoogleRules(NimbusJwtDecoder decoder, String clientId) {
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                new JwtClaimValidator<String>(JwtClaimNames.ISS, ISSUERS::contains),
                new JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(clientId))));
        return decoder;
    }

    /** The token's address if the token is valid and Google verified the address. */
    public Optional<String> verifiedEmail(String idToken) {
        if (decoder == null || idToken == null || idToken.isBlank()) return Optional.empty();
        try {
            Jwt jwt = decoder.decode(idToken);
            if (!Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) return Optional.empty();
            return Optional.ofNullable(jwt.getClaimAsString("email"));
        } catch (JwtException e) {
            return Optional.empty();
        }
    }
}
