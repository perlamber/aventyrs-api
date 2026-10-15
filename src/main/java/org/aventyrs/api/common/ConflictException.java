package org.aventyrs.api.common;

/**
 * The request is well formed but clashes with what is stored. For example, a Bestiário foe already
 * standing in another Cena (core 0.1.7.5). Mapped to {@code 409} by {@link GlobalExceptionHandler}.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
