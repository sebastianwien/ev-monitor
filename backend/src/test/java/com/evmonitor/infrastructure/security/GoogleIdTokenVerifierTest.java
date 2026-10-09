package com.evmonitor.infrastructure.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleIdTokenVerifierTest {

    private static final String CLIENT_ID = "client-123.apps.googleusercontent.com";
    private final KeyPair keys = rsaKeys();
    private final GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(
            GoogleIdTokenVerifier.withGoogleRules(NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build(), CLIENT_ID));

    @Test
    void validTokenYieldsTheVerifiedAddress() {
        assertThat(verifier.verifiedEmail(token(claims().build()))).contains("neu@gmail.com");
    }

    @Test
    void foreignAudienceIsRejected() {
        assertThat(verifier.verifiedEmail(token(claims().audience("other-client").build()))).isEmpty();
    }

    @Test
    void foreignIssuerIsRejected() {
        assertThat(verifier.verifiedEmail(token(claims().issuer("https://evil.example.com").build()))).isEmpty();
    }

    @Test
    void unverifiedAddressIsRejected() {
        assertThat(verifier.verifiedEmail(token(claims().claim("email_verified", false).build()))).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        Instant past = Instant.now().minusSeconds(7200);
        JWTClaimsSet expired = claims().issueTime(Date.from(past)).expirationTime(Date.from(past.plusSeconds(600))).build();
        assertThat(verifier.verifiedEmail(token(expired))).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        assertThat(verifier.verifiedEmail("not-a-jwt")).isEmpty();
    }

    @Test
    void withoutClientIdNothingIsAccepted() {
        assertThat(GoogleIdTokenVerifier.disabled().verifiedEmail(token(claims().build()))).isEmpty();
    }

    private static JWTClaimsSet.Builder claims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com")
                .audience(CLIENT_ID)
                .subject("10769150350006150715113082367")
                .claim("email", "neu@gmail.com")
                .claim("email_verified", true)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)));
    }

    private String token(JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner(keys.getPrivate()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static KeyPair rsaKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
