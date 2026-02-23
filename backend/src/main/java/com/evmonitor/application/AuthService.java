package com.evmonitor.application;

import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.security.JwtService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email is already in use.");
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = User.createNewLocalUser(request.email(), encodedPassword);

        User savedUser = userRepository.save(user);

        String jwtToken = jwtService.generateToken(UserPrincipal.create(savedUser));

        return new AuthResponse(
                jwtToken,
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getRole());
    }

    public AuthResponse login(LoginRequest request) {
        // Authenticate the user via Spring Security AuthenticationManager
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.password()));

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(); // Shouldn't happen since authentication passed

        String jwtToken = jwtService.generateToken(UserPrincipal.create(user));

        return new AuthResponse(
                jwtToken,
                user.getId(),
                user.getEmail(),
                user.getRole());
    }
}
