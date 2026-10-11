package com.evmonitor.application.user;

/**
 * password: required for accounts that have one. code: mail code (POST /auth/code/request) for
 * accounts without password (Google, mail code). Both checked in UserService.deleteAccount.
 */
public record DeleteAccountRequest(
        String password,
        String code
) {
}
