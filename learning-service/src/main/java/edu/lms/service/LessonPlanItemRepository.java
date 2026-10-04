package edu.lms.service;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface LessonPlanItemRepository extends JpaRepository<LessonPlanItem, Long> {

    List<LessonPlanItem> findByCourseIdAndTermIdOrderByClassNumberAsc(String courseId, String termId);

    List<LessonPlanItem> findByCourseIdInAndTermIdOrderByClassDateAscClassNumberAsc(
            Collection<String> courseIds, String termId);
}
