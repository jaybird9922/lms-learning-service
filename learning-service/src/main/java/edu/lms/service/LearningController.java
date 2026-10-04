package edu.lms.service;

import edu.lms.service.LearningDtos.CourseRequest;
import edu.lms.service.LearningDtos.CourseRow;
import edu.lms.service.LearningDtos.MarkRequest;
import edu.lms.service.LearningDtos.MarkResult;
import edu.lms.service.LearningDtos.Overview;
import edu.lms.service.LearningDtos.PlanResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;

/**
 * learning-service: the four endpoints from service.html, admin CRUD on /courses (Phase 1), and /whoami
 * for the profile demo.
 * Base path must match the gateway route (see application.yml).
 */
@RestController
@RequestMapping("/api/learning")
public class LearningController {

    private final LearningService service;
    private final CallerResolver callers;

    public LearningController(LearningService service, CallerResolver callers) {
        this.service = service;
        this.callers = callers;
    }

    /** Courses (role-aware). */
    @GetMapping("/classes")
    public List<CourseRow> classes(HttpServletRequest request) {
        return service.classes(callers.resolve(request));
    }

    /** Learning overview (role-aware stat tiles). */
    @GetMapping("/overview")
    public Overview overview(HttpServletRequest request) {
        return service.overview(callers.resolve(request));
    }

    /** Lesson plan of a course. 404 unknown course, 403 not enrolled / not your course. */
    @GetMapping("/plan/{courseId}")
    public PlanResponse plan(@PathVariable String courseId, HttpServletRequest request) {
        return service.plan(courseId, callers.resolve(request));
    }

    /** Mark a class complete / not complete. Teacher (own course) or admin. Body: {"completed": true|false}. */
    @PutMapping("/plan/items/{id}")
    public MarkResult mark(@PathVariable Long id, @RequestBody MarkRequest body, HttpServletRequest request) {
        if (body == null || body.completed() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body must be {\"completed\": true|false}");
        }
        return service.mark(id, body.completed(), callers.resolve(request));
    }

    // ---- /courses: simple CRUD on the course records (admin only) ----

    @GetMapping("/courses")
    public List<Course> listCourses(HttpServletRequest request) {
        return service.listCourses(callers.resolve(request));
    }

    @GetMapping("/courses/{id}")
    public Course getCourse(@PathVariable String id, HttpServletRequest request) {
        return service.getCourse(id, callers.resolve(request));
    }

    @PostMapping("/courses")
    public ResponseEntity<Course> createCourse(@RequestBody CourseRequest body, HttpServletRequest request) {
        Course created = service.createCourse(body, callers.resolve(request));
        return ResponseEntity.created(URI.create("/api/learning/courses/" + created.getId())).body(created);
    }

    @PutMapping("/courses/{id}")
    public Course updateCourse(@PathVariable String id, @RequestBody CourseRequest body, HttpServletRequest request) {
        return service.updateCourse(id, body, callers.resolve(request));
    }

    @DeleteMapping("/courses/{id}")
    public ResponseEntity<Void> deleteCourse(@PathVariable String id, HttpServletRequest request) {
        service.deleteCourse(id, callers.resolve(request));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/whoami")
    public String whoami(@Value("${lms.environment-label}") String label) {
        return label;
    }
}
