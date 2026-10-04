package edu.lms.service;

import edu.lms.service.LearningDtos.CourseRequest;
import edu.lms.service.LearningDtos.CourseRow;
import edu.lms.service.LearningDtos.ItemDto;
import edu.lms.service.LearningDtos.MarkResult;
import edu.lms.service.LearningDtos.Overview;
import edu.lms.service.LearningDtos.PlanResponse;
import edu.lms.service.LearningDtos.Stat;
import edu.lms.service.LearningDtos.TermInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Business rules for the four learning endpoints. Role scoping lives here:
 * student sees enrolled courses, teacher sees own courses, admin sees all.
 */
@Service
public class LearningService {

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US);
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a", Locale.US);

    private final CourseRepository courses;
    private final LessonPlanItemRepository planItems;
    private final StudySessionRepository studySessions;
    private final TermRepository terms;
    private final EnrollmentClient enrollment;
    private final Clock clock;

    @Autowired
    public LearningService(CourseRepository courses, LessonPlanItemRepository planItems,
                           StudySessionRepository studySessions, TermRepository terms,
                           EnrollmentClient enrollment) {
        this(courses, planItems, studySessions, terms, enrollment, Clock.systemDefaultZone());
    }

    /** Lets a test pin "today". */
    LearningService(CourseRepository courses, LessonPlanItemRepository planItems,
                    StudySessionRepository studySessions, TermRepository terms,
                    EnrollmentClient enrollment, Clock clock) {
        this.courses = courses;
        this.planItems = planItems;
        this.studySessions = studySessions;
        this.terms = terms;
        this.enrollment = enrollment;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- GET /classes

    @Transactional(readOnly = true)
    public List<CourseRow> classes(Caller caller) {
        Term term = currentTerm();
        List<Course> visible = visibleCourses(caller);
        Map<String, List<LessonPlanItem>> byCourse = itemsByCourse(visible, term);
        LocalDate today = today();
        return visible.stream()
                .map(c -> row(c, byCourse.getOrDefault(c.getId(), List.of()), caller, today))
                .toList();
    }

    // ---------------------------------------------------------------- GET /plan/{courseId}

    @Transactional(readOnly = true)
    public PlanResponse plan(String courseId, Caller caller) {
        Term term = currentTerm();
        Course course = courses.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such course"));
        if (!canView(course, caller)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not enrolled / not your course");
        }
        List<LessonPlanItem> items = planItems.findByCourseIdAndTermIdOrderByClassNumberAsc(courseId, term.getId());
        LocalDate today = today();
        CourseRow r = row(course, items, caller, today);
        List<ItemDto> itemDtos = items.stream().map(i -> item(i, today)).toList();
        return new PlanResponse(r.id(), r.name(), r.gradeLevel(), r.teacher(), r.teacherUsername(), r.color(),
                r.classes(), r.classesCompleted(), r.classesRemaining(), r.percent(), r.status(),
                r.nextClass(), r.canMark(), termInfo(term), itemDtos);
    }

    // ---------------------------------------------------------------- PUT /plan/items/{id}

    @Transactional
    public MarkResult mark(Long itemId, boolean completed, Caller caller) {
        LessonPlanItem target = planItems.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such class"));
        Course course = courses.findById(target.getCourseId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such course"));
        if (!canMark(course, caller)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your course");
        }

        target.setCompleted(completed);
        target.setCompletedAt(completed ? LocalDateTime.now(clock) : null);
        target.setCompletedBy(completed ? caller.username() : null);
        planItems.save(target);

        // Recompute from the whole plan; `target` is the same managed instance as in this list.
        List<LessonPlanItem> all = planItems.findByCourseIdAndTermIdOrderByClassNumberAsc(
                course.getId(), target.getTermId());
        int total = all.size();
        int done = countCompleted(all);
        course.setPercent(percent(done, total));
        course.setStatus(statusOf(done, total));
        courses.save(course);

        return new MarkResult(item(target, today()), course.getId(), course.getPercent(),
                course.getStatus(), done, total);
    }

    // ---------------------------------------------------------------- GET /overview

    @Transactional(readOnly = true)
    public Overview overview(Caller caller) {
        Term term = currentTerm();
        List<Course> visible = visibleCourses(caller);
        Map<String, List<LessonPlanItem>> byCourse = itemsByCourse(visible, term);

        List<LessonPlanItem> all = byCourse.values().stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparing(LessonPlanItem::getClassDate)
                        .thenComparing(LessonPlanItem::getClassNumber)
                        .thenComparing(LessonPlanItem::getCourseId))   // deterministic when two courses tie
                .toList();
        int total = all.size();
        int done = countCompleted(all);
        int progress = percent(done, total);

        List<Stat> stats = new ArrayList<>();
        switch (caller.role()) {
            case STUDENT -> {
                stats.add(new Stat("Enrolled Classes", String.valueOf(visible.size()), "This term",
                        "blue", "classes", null));
                stats.add(new Stat("Classes Completed", done + " / " + total, (total - done) + " remaining",
                        "green", "check", progress));
                stats.add(nextClassTile(all, visible, "purple"));
                stats.add(studyHoursTile(term, caller, false));
            }
            case TEACHER -> {
                stats.add(new Stat("Courses Taught", String.valueOf(visible.size()), "This term",
                        "blue", "classes", null));
                stats.add(new Stat("Classes This Term", String.valueOf(total), "Across my courses",
                        "green", "lessons", null));
                stats.add(new Stat("Classes Completed", done + " / " + total, "Marked complete",
                        "purple", "check", progress));
                stats.add(nextClassTile(all, visible, "amber"));
            }
            case ADMIN -> {
                stats.add(new Stat("Courses", String.valueOf(visible.size()), "All teachers",
                        "blue", "classes", null));
                stats.add(new Stat("Classes This Term", String.valueOf(total), "Across all courses",
                        "green", "lessons", null));
                stats.add(new Stat("Classes Completed", done + " / " + total, "Marked by teachers",
                        "purple", "check", progress));
                stats.add(studyHoursTile(term, caller, true));
            }
        }
        return new Overview(termInfo(term), stats, caller.role().name().toLowerCase(Locale.ROOT));
    }

    // ---------------------------------------------------------------- /courses CRUD (admin)
    // Phase 1 "simple CRUD" surface. The role-aware read of courses is GET /classes; these
    // endpoints manage the course records themselves, so they are admin-only.

    @Transactional(readOnly = true)
    public List<Course> listCourses(Caller caller) {
        requireAdmin(caller);
        return courses.findAll().stream().sorted(Comparator.comparing(Course::getName)).toList();
    }

    @Transactional(readOnly = true)
    public Course getCourse(String id, Caller caller) {
        requireAdmin(caller);
        return courses.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such course"));
    }

    @Transactional
    public Course createCourse(CourseRequest req, Caller caller) {
        requireAdmin(caller);
        if (req == null || isBlank(req.id()) || isBlank(req.name())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id and name are required");
        }
        String id = req.id().trim();
        if (courses.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Course already exists");
        }
        Course c = new Course();
        c.setId(id);
        apply(c, req);
        return courses.save(c);
    }

    @Transactional
    public Course updateCourse(String id, CourseRequest req, Caller caller) {
        requireAdmin(caller);
        if (req == null || isBlank(req.name())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        Course c = courses.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such course"));
        apply(c, req);
        return courses.save(c);
    }

    /** The database cascades the delete to the course's lesson plan items. */
    @Transactional
    public void deleteCourse(String id, Caller caller) {
        requireAdmin(caller);
        if (!courses.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such course");
        }
        courses.deleteById(id);
    }

    private static void apply(Course c, CourseRequest req) {
        c.setName(req.name().trim());
        c.setGradeLevel(req.gradeLevel());
        c.setTeacher(req.teacher());
        c.setTeacherUsername(req.teacherUsername());
        if (!isBlank(req.color())) {
            c.setColor(req.color().trim());
        }
    }

    private static void requireAdmin(Caller caller) {
        if (caller.role() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // ---------------------------------------------------------------- helpers

    private Term currentTerm() {
        return terms.findFirstByCurrentTermTrue()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "No current term configured"));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private List<Course> visibleCourses(Caller caller) {
        List<Course> list = switch (caller.role()) {
            case STUDENT -> {
                List<String> ids = enrollment.enrolledCourseIds(caller);
                yield ids.isEmpty() ? List.<Course>of() : courses.findAllById(ids);
            }
            case TEACHER -> courses.findByTeacherUsername(caller.username());
            case ADMIN -> courses.findAll();
        };
        return list.stream().sorted(Comparator.comparing(Course::getName)).toList();
    }

    private boolean canView(Course course, Caller caller) {
        return switch (caller.role()) {
            case ADMIN -> true;
            case TEACHER -> caller.username().equals(course.getTeacherUsername());
            case STUDENT -> enrollment.enrolledCourseIds(caller).contains(course.getId());
        };
    }

    /** Teacher on their own course, or admin. Students never. */
    private boolean canMark(Course course, Caller caller) {
        return switch (caller.role()) {
            case ADMIN -> true;
            case TEACHER -> caller.username().equals(course.getTeacherUsername());
            case STUDENT -> false;
        };
    }

    private Map<String, List<LessonPlanItem>> itemsByCourse(List<Course> visible, Term term) {
        if (visible.isEmpty()) {
            return Map.of();
        }
        List<String> ids = visible.stream().map(Course::getId).toList();
        return planItems.findByCourseIdInAndTermIdOrderByClassDateAscClassNumberAsc(ids, term.getId())
                .stream()
                .collect(Collectors.groupingBy(LessonPlanItem::getCourseId, LinkedHashMap::new,
                        Collectors.toList()));
    }

    private CourseRow row(Course course, List<LessonPlanItem> items, Caller caller, LocalDate today) {
        int total = items.size();
        int done = countCompleted(items);
        ItemDto next = items.stream()
                .filter(i -> !i.isCompleted())
                .findFirst()
                .map(i -> item(i, today))
                .orElse(null);
        return new CourseRow(course.getId(), course.getName(), course.getGradeLevel(), course.getTeacher(),
                course.getTeacherUsername(), course.getColor(), total, done, total - done,
                percent(done, total), statusOf(done, total), next, canMark(course, caller));
    }

    private ItemDto item(LessonPlanItem i, LocalDate today) {
        String status;
        if (i.isCompleted()) {
            status = "Completed";
        } else if (i.getClassDate().equals(today)) {
            status = "Today";
        } else if (i.getClassDate().isBefore(today)) {
            status = "Not completed";
        } else {
            status = "Upcoming";
        }
        return new ItemDto(i.getId(), i.getClassNumber(), i.getClassDate().toString(),
                i.getClassDate().format(DAY_LABEL), i.getTitle(), i.getTopic(), i.isCompleted(),
                i.getCompletedAt() == null ? null : i.getCompletedAt().format(STAMP),
                i.getCompletedBy(), status);
    }

    private Stat nextClassTile(List<LessonPlanItem> all, List<Course> visible, String color) {
        LessonPlanItem next = all.stream().filter(i -> !i.isCompleted()).findFirst().orElse(null);
        if (next == null) {
            return new Stat("Next Class", "None", "All classes complete", color, "schedule", null);
        }
        String courseName = visible.stream()
                .filter(c -> c.getId().equals(next.getCourseId()))
                .map(Course::getName)
                .findFirst()
                .orElse(next.getCourseId());
        return new Stat("Next Class", next.getClassDate().format(DAY_LABEL),
                courseName + " · class #" + next.getClassNumber(), color, "schedule", null);
    }

    /** Study hours this term: just the caller for a student, every student for an admin. */
    private Stat studyHoursTile(Term term, Caller caller, boolean everyone) {
        List<StudySession> sessions = everyone ? studySessions.findAll()
                : studySessions.findByUsername(caller.username());
        int minutes = sessions.stream()
                .filter(s -> !s.getDay().isBefore(term.getStartDate()) && !s.getDay().isAfter(term.getEndDate()))
                .mapToInt(StudySession::getMinutes)
                .sum();
        String value = String.format(Locale.US, "%.1f", minutes / 60.0);
        return new Stat("Study Hours", value, everyone ? "All students, this term" : "This term",
                "amber", "clock", null);
    }

    private TermInfo termInfo(Term t) {
        return new TermInfo(t.getName(), t.getStartDate().format(LONG_DATE), t.getEndDate().format(LONG_DATE));
    }

    private static int countCompleted(List<LessonPlanItem> items) {
        return (int) items.stream().filter(LessonPlanItem::isCompleted).count();
    }

    static int percent(int done, int total) {
        return total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
    }

    static String statusOf(int done, int total) {
        if (total > 0 && done == total) {
            return "completed";
        }
        return done > 0 ? "in-progress" : "not-started";
    }
}
