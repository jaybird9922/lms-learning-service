package edu.lms.service;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, String> {

    List<Course> findByTeacherUsername(String teacherUsername);
}
