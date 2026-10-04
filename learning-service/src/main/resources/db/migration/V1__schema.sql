-- learningdb schema, matching database.html (learning-service: course, lesson_plan_item, study_session, term).
-- Runs in every profile. Flyway applies it once and records it in flyway_schema_history.

CREATE TABLE term (
    id            text    PRIMARY KEY,
    name          text    NOT NULL,
    start_date    date    NOT NULL,
    end_date      date    NOT NULL,
    is_current    boolean NOT NULL DEFAULT false,
    max_credits   integer NOT NULL DEFAULT 30,
    quarter       integer,
    academic_year text
);

CREATE TABLE course (
    id               text    PRIMARY KEY,
    name             text    NOT NULL,
    grade_level      text,
    teacher          text,
    color            text    NOT NULL DEFAULT 'blue',
    percent          integer NOT NULL DEFAULT 0,
    status           text    NOT NULL DEFAULT 'not-started',
    teacher_username text
);
CREATE INDEX idx_course_teacher_username ON course (teacher_username);

-- term_id is a plain id (no foreign key), same as the diagram.
CREATE TABLE lesson_plan_item (
    id           bigserial PRIMARY KEY,
    course_id    text      NOT NULL REFERENCES course (id) ON DELETE CASCADE,
    term_id      text      NOT NULL,
    class_number integer   NOT NULL,
    class_date   date      NOT NULL,
    title        text      NOT NULL,
    topic        text      NOT NULL,
    completed    boolean   NOT NULL DEFAULT false,
    completed_at timestamp,
    completed_by text,
    CONSTRAINT uq_lesson_plan_item_course_class UNIQUE (course_id, class_number)
);
CREATE INDEX idx_lesson_plan_item_course_date ON lesson_plan_item (course_id, class_date);

CREATE TABLE study_session (
    id       bigserial PRIMARY KEY,
    username text      NOT NULL,
    day      date      NOT NULL,
    minutes  integer   NOT NULL
);
CREATE INDEX idx_study_session_username_day ON study_session (username, day);
