package com.evmonitor.application.user;

/** password: required only for accounts that have one (checked in UserService.deleteAccount). */
public record DeleteAccountRequest(
        String password
) {
}
