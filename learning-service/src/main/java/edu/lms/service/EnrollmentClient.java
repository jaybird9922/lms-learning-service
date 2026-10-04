package edu.lms.service;

import java.util.List;

/**
 * Answers "which course ids is this student enrolled in?". enrollment-service (Renae) owns
 * that answer: GET /api/enrollment/state returns {"enrolled": ["ap-precalc", ...]}.
 * Two implementations, picked by lms.enrollment.mode: stub (default) and rest.
 */
public interface EnrollmentClient {

    List<String> enrolledCourseIds(Caller caller);
}
