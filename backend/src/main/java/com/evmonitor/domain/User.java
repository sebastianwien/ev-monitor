package com.evmonitor.domain;

import java.time.LocalDateTime;
import java.util.UUID;

public class User {
    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final AuthProvider authProvider;
    private final String role;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public User(UUID id, String email, String passwordHash, AuthProvider authProvider, String role,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (id == null)
            throw new IllegalArgumentException("User ID cannot be null");
        if (email == null || email.isBlank())
            throw new IllegalArgumentException("Email cannot be empty");
        if (authProvider == null)
            throw new IllegalArgumentException("Auth Provider cannot be null");

        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.authProvider = authProvider;
        this.role = role == null ? "USER" : role;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static User createNewLocalUser(String email, String passwordHash) {
        LocalDateTime now = LocalDateTime.now();
        return new User(UUID.randomUUID(), email, passwordHash, AuthProvider.LOCAL, "USER", now, now);
    }

    public static User createNewSsoUser(String email, AuthProvider authProvider) {
        LocalDateTime now = LocalDateTime.now();
        return new User(UUID.randomUUID(), email, null, authProvider, "USER", now, now);
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
}
