package com.evmonitor.infrastructure.security;

import com.evmonitor.domain.AuthProvider;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Optional;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Value("${app.oauth2.authorized-redirect-uris:http://localhost:5173/oauth2/redirect}")
    private String redirectUri;

    public OAuth2AuthenticationSuccessHandler(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        OAuth2AuthenticationToken authToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = authToken.getPrincipal();

        String registrationId = authToken.getAuthorizedClientRegistrationId();
        AuthProvider authProvider = determineAuthProvider(registrationId);

        String email = oAuth2User.getAttribute("email");
        if (email == null) {
            // Some providers might not return email or it might be named differently
            throw new IllegalArgumentException("Email not found from OAuth2 provider");
        }

        // Check if user exists, otherwise create
        Optional<User> userOptional = userRepository.findByEmail(email);
        User user;
        if (userOptional.isPresent()) {
            user = userOptional.get();
            if (!user.getAuthProvider().equals(authProvider)) {
                throw new IllegalArgumentException("Looks like you're signed up with " +
                        user.getAuthProvider() + " account. Please use your " + user.getAuthProvider() +
                        " account to login.");
            }
        } else {
            user = User.createNewSsoUser(email, authProvider);
            user = userRepository.save(user);
        }

        UserPrincipal userPrincipal = UserPrincipal.create(user, oAuth2User.getAttributes());
        String token = jwtService.generateToken(userPrincipal);

        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("token", token)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private AuthProvider determineAuthProvider(String registrationId) {
        if (registrationId.equalsIgnoreCase("google")) {
            return AuthProvider.GOOGLE;
        } else if (registrationId.equalsIgnoreCase("facebook")) {
            return AuthProvider.FACEBOOK;
        } else if (registrationId.equalsIgnoreCase("apple")) {
            return AuthProvider.APPLE;
        }
        return AuthProvider.LOCAL;
    }
}
