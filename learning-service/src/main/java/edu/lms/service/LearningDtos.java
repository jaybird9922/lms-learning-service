package edu.lms.service;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Response shapes, copied from the examples in service.html. */
public final class LearningDtos {

    private LearningDtos() {
    }

    /** {"name": "Q1 · Fall 2026", "start": "Aug 24, 2026", "end": "Oct 23, 2026"} */
    public record TermInfo(String name, String start, String end) {
    }

    /** One scheduled class. status: Completed | Today | Not completed | Upcoming. */
    public record ItemDto(Long id, Integer classNumber, String date, String dateLabel, String title,
                          String topic, boolean completed, String completedAt, String completedBy,
                          String status) {
    }

    /** One row of GET /classes. */
    public record CourseRow(String id, String name, String gradeLevel, String teacher,
                            String teacherUsername, String color, int classes, int classesCompleted,
                            int classesRemaining, int percent, String status, ItemDto nextClass,
                            boolean canMark) {
    }

    /** GET /plan/{courseId}: the course row fields plus the term and every item. */
    public record PlanResponse(String id, String name, String gradeLevel, String teacher,
                               String teacherUsername, String color, int classes, int classesCompleted,
                               int classesRemaining, int percent, String status, ItemDto nextClass,
                               boolean canMark, TermInfo term, List<ItemDto> items) {
    }

    /** POST / PUT /courses body. percent and status are derived from the lesson plan, so they are not accepted. */
    public record CourseRequest(String id, String name, String gradeLevel, String teacher,
                                String teacherUsername, String color) {
    }

    /** PUT /plan/items/{id} request body. */
    public record MarkRequest(Boolean completed) {
    }

    /** PUT /plan/items/{id} response. */
    public record MarkResult(ItemDto item, String courseId, int coursePercent, String courseStatus,
                             int completed, int total) {
    }

    /** One stat tile. progress is only present on the "Classes Completed" tile. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Stat(String label, String value, String sub, String color, String icon,
                       Integer progress) {
    }

    /** GET /overview. */
    public record Overview(TermInfo term, List<Stat> stats, String role) {
    }
}
