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

        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Username is already taken.");
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = User.createNewLocalUser(request.email(), request.username(), encodedPassword);

        User savedUser = userRepository.save(user);

        String jwtToken = jwtService.generateToken(UserPrincipal.create(savedUser));

        return new AuthResponse(
                jwtToken,
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getRole());
    }

    public AuthResponse login(LoginRequest request) {
        // Try to find user by email first, then by username
        User user = userRepository.findByEmail(request.email())
                .or(() -> userRepository.findByUsername(request.email())) // request.email() field is used for username too
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        // Authenticate the user via Spring Security AuthenticationManager
        // Use the actual email from the found user for authentication
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        user.getEmail(),
                        request.password()));

        String jwtToken = jwtService.generateToken(UserPrincipal.create(user));

        return new AuthResponse(
                jwtToken,
                user.getId(),
                user.getEmail(),
                user.getRole());
    }
}
