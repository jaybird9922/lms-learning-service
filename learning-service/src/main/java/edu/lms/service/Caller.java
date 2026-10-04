package edu.lms.service;

/**
 * Who is calling. In the finished system this comes from the Keycloak JWT.
 * Until Keycloak is wired in, CallerResolver builds it from request headers.
 *
 * @param authorization raw Authorization header (may be null), forwarded to other services
 */
public record Caller(String username, Role role, String authorization) {
}
