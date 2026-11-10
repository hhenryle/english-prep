package com.englishprep.api.identity.api.security;

/**
 * Who is calling: the member whose access token was accepted. It only holds the id, taken from the
 * token, so nothing is read from the database to know it. Controllers receive it with
 * {@code @AuthenticationPrincipal AuthenticatedMember member}.
 */
public record AuthenticatedMember(long id) {
}
