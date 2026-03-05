package com.evmonitor.application.tesla;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OAuth2 token response from Tesla's auth server.
 */
public record TeslaFleetTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("id_token") String idToken
) {}
