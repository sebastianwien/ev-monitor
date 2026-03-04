package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/unsubscribe")
public class UnsubscribeController {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Value("${app.base-url}")
    private String baseUrl;

    public UnsubscribeController(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<Void> unsubscribe(@RequestParam String token) {
        try {
            String email = jwtService.extractEmailFromUnsubscribeToken(token);
            userRepository.findByEmail(email).ifPresent(user ->
                    userRepository.disableEmailNotifications(user.getId()));
        } catch (Exception e) {
            // Invalid token: redirect anyway to avoid leaking info
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(baseUrl + "?unsubscribed=true"));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}
