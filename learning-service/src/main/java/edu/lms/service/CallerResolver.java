package edu.lms.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * STAND-IN for Keycloak. Reads the caller from two headers:
 *
 *     X-User: student1        X-Role: student | teacher | admin
 *
 * If a header is missing, lms.auth.default-username / default-role are used. Those
 * are set in the dev profile only, so prod answers 401 unless both headers are sent.
 *
 * When Keycloak arrives, replace the body of resolve() with a read of the JWT
 * (preferred_username + the realm role) and nothing else in the service changes.
 */
@Component
public class CallerResolver {

    private final String defaultUsername;
    private final String defaultRole;

    public CallerResolver(@Value("${lms.auth.default-username:}") String defaultUsername,
                          @Value("${lms.auth.default-role:}") String defaultRole) {
        this.defaultUsername = defaultUsername;
        this.defaultRole = defaultRole;
    }

    public Caller resolve(HttpServletRequest request) {
        String username = firstNonBlank(request.getHeader("X-User"), defaultUsername);
        String rawRole = firstNonBlank(request.getHeader("X-Role"), defaultRole);
        if (username == null || rawRole == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Send X-User and X-Role headers (stand-in for Keycloak)");
        }
        Role role = Role.parseOrNull(rawRole);
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "X-Role must be student, teacher or admin");
        }
        return new Caller(username.trim(), role, request.getHeader("Authorization"));
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
