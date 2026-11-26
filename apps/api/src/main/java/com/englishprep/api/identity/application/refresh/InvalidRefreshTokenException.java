package com.englishprep.api.identity.application.refresh;

/**
 * A refresh token cannot be used. One message for every reason (unknown, expired, already used,
 * member gone), so the caller learns nothing about which tokens exist.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Invalid or expired refresh token");
    }
}
