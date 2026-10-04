package edu.lms.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

/**
 * Real call to enrollment-service. Turn on with ENROLLMENT_MODE=rest (and ENROLLMENT_URL if
 * the host/port differs). Inside the compose network use the service name, not localhost.
 *
 * The caller's identity is forwarded: Authorization (once Keycloak exists) plus the
 * X-User / X-Role stand-in headers.
 */
@Component
@ConditionalOnProperty(name = "lms.enrollment.mode", havingValue = "rest")
public class RestEnrollmentClient implements EnrollmentClient {

    private final RestClient http;

    public RestEnrollmentClient(@Value("${lms.enrollment.url:http://enrollment-service:8104}") String baseUrl) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public List<String> enrolledCourseIds(Caller caller) {
        try {
            RestClient.RequestHeadersSpec<?> request = http.get()
                    .uri("/api/enrollment/state")
                    .header("X-User", caller.username())
                    .header("X-Role", caller.role().name().toLowerCase(Locale.ROOT));
            if (caller.authorization() != null && !caller.authorization().isBlank()) {
                request = request.header("Authorization", caller.authorization());
            }
            EnrollmentState state = request.retrieve().body(EnrollmentState.class);
            return (state == null || state.enrolled() == null) ? List.of() : state.enrolled();
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "enrollment-service unavailable: " + e.getMessage());
        }
    }

    /** Shape of GET /api/enrollment/state. */
    record EnrollmentState(List<String> enrolled) {
    }
}
