package edu.lms.service;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * learningdb.course. The id is the same slug enrollment-service uses (e.g. "ap-precalc").
 * percent and status are kept in step with the lesson plan whenever a class is marked.
 */
@Entity
@Table(name = "course", indexes = @Index(columnList = "teacher_username"))
public class Course {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    private String gradeLevel;

    private String teacher;

    @Column(nullable = false)
    private String color = "blue";

    @Column(nullable = false)
    private int percent = 0;

    @Column(nullable = false)
    private String status = "not-started";

    private String teacherUsername;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getGradeLevel() { return gradeLevel; }
    public void setGradeLevel(String gradeLevel) { this.gradeLevel = gradeLevel; }

    public String getTeacher() { return teacher; }
    public void setTeacher(String teacher) { this.teacher = teacher; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public int getPercent() { return percent; }
    public void setPercent(int percent) { this.percent = percent; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTeacherUsername() { return teacherUsername; }
    public void setTeacherUsername(String teacherUsername) { this.teacherUsername = teacherUsername; }
}
