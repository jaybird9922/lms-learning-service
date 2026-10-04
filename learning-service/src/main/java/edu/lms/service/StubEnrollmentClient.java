package edu.lms.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Default while enrollment-service is not running. Reads a comma-separated list from
 * application.yml:   lms.enrollment.stub.<username>: ap-precalc,ap-chemistry
 * Unknown users (and teachers/admins) are enrolled in nothing.
 */
@Component
@ConditionalOnProperty(name = "lms.enrollment.mode", havingValue = "stub", matchIfMissing = true)
public class StubEnrollmentClient implements EnrollmentClient {

    private final Environment env;

    public StubEnrollmentClient(Environment env) {
        this.env = env;
    }

    @Override
    public List<String> enrolledCourseIds(Caller caller) {
        String raw = env.getProperty("lms.enrollment.stub." + caller.username());
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
