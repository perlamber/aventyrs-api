package org.aventyrs.api.auth;

/**
 * Login failed. Deliberately says nothing about which half was wrong — an unknown login, a wrong
 * password and an account with no password yet all read the same, so the endpoint can't be used
 * to probe which logins exist.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid login or password");
    }
}
