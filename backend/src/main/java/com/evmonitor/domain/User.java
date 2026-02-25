package com.evmonitor.domain;

import java.time.LocalDateTime;
import java.util.UUID;

public class User {
    private final UUID id;
    private final String email;
    private final String username;
    private final String passwordHash;
    private final AuthProvider authProvider;
    private final String role;
    private final boolean emailVerified;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public User(UUID id, String email, String username, String passwordHash, AuthProvider authProvider, String role,
            boolean emailVerified, LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (id == null)
            throw new IllegalArgumentException("User ID cannot be null");
        if (email == null || email.isBlank())
            throw new IllegalArgumentException("Email cannot be empty");
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("Username cannot be empty");
        if (authProvider == null)
            throw new IllegalArgumentException("Auth Provider cannot be null");

        this.id = id;
        this.email = email;
        this.username = username;
        this.passwordHash = passwordHash;
        this.authProvider = authProvider;
        this.role = role == null ? "USER" : role;
        this.emailVerified = emailVerified;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static User createNewLocalUser(String email, String username, String passwordHash) {
        LocalDateTime now = LocalDateTime.now();
        return new User(UUID.randomUUID(), email, username, passwordHash, AuthProvider.LOCAL, "USER", false, now, now);
    }

    public static User createVerifiedLocalUser(String email, String username, String passwordHash) {
        LocalDateTime now = LocalDateTime.now();
        return new User(UUID.randomUUID(), email, username, passwordHash, AuthProvider.LOCAL, "USER", true, now, now);
    }

    public static User createNewSsoUser(String email, String username, AuthProvider authProvider) {
        LocalDateTime now = LocalDateTime.now();
        // SSO emails are already verified by the OAuth provider
        return new User(UUID.randomUUID(), email, username, null, authProvider, "USER", true, now, now);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public AuthProvider getAuthProvider() {
        return authProvider;
    }

    public String getRole() {
        return role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getUsername() {
        return username;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }
}
