package com.evmonitor.application;

public record RegisterRequest(
        String email,
        String password) {
}
