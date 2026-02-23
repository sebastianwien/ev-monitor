package com.evmonitor.application;

public record LoginRequest(
        String email,
        String password) {
}
