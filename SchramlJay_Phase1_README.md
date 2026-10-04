# LearningHub
## learning-service
**Jay Bruhn Schraml**

## Phase 1

This microservice owns the *Learning* bounded context: the courses a school tracks progress for, the
term lesson plan of each course, and the progress numbers that students, teachers and admins see.

- Port = `8105`

- database = `learningdb`

- base path = `/api/learning`

---

## Deploy and run

**Prerequisites:** Docker Desktop. Nothing else, because Java and Maven build inside the container.

    git clone https://github.com/David-Coulter/lms-skeleton.git
    cd lms-skeleton
    docker compose up --build learning-service

Three containers come up: `postgres`, `config-service` (the Spring Cloud Config Server, port 8888), and
`learning-service` on 8105. The service waits on the config server's health check before starting.

The first build takes a few minutes while Maven downloads dependencies. Wait for
`Started LearningServiceApplication`, then from another terminal:

    curl localhost:8105/actuator/health
    curl localhost:8105/api/learning/classes
    curl localhost:8105/api/learning/whoami

    docker compose exec postgres psql -U lms -d learningdb -c '\dt'

Health `UP`, a populated JSON array, a `whoami` label that says `served by config-service`, and the four tables
in `learningdb` (plus `flyway_schema_history`) means the whole path is working: container, service, config
server and database.

To stop: `docker compose down`, or `docker compose down -v` to also wipe the database volume and start fresh.

### What's in the repository

    learning-service/   this service
    config-service/     Spring Cloud Config Server (learning-service-dev.yml / -prod.yml live here)
    docker-compose.yml  postgres + config-service + the services
    postgres/init.sql   creates one database per service
    template/           service skeleton the team shares
    new-service.sh      generates a service from the template

---

## Canonical model

The whole context is a course, its lesson plan, and a little supporting data.

### `course`

A subject this service tracks progress for.

| Group | Fields |
|-------|--------|
| Identity | `id` (text slug shared with enrollment-service, e.g. `ap-precalc`), `name`, `color` |
| People | `teacher`, `teacher_username` |
| Level | `grade_level` |
| Derived | `percent` (default 0), `status` (default `not-started`), recomputed whenever a class is marked |

### `lesson_plan_item`

One scheduled class of one course in one term: `course_id` (FK to `course`, cascading on delete), `term_id`,
`class_number`, `class_date`, `title`, `topic`, `completed`, `completed_at`, `completed_by`.
`(course_id, class_number)` is unique.

### `study_session`

Minutes a student studied on a day (`username`, `day`, `minutes`). It only feeds the "Study Hours" tile.

### `term`

The term the plans belong to: `id`, `name`, `start_date`, `end_date`, `is_current`, `max_credits`, `quarter`,
`academic_year`.

### Decisions that were made

**`username` is the identity, not a local id.** `teacher_username`, `completed_by` and `study_session.username`
are plain text, the same join key every other LearningHub service uses. Nothing here holds another service's
primary key.

**Cross-service references are ids only.** `term_id` is a plain value, and a student's enrolled courses come from
enrollment-service (`GET /api/enrollment/state`) rather than from a table here. The one foreign key is inside
this database: `lesson_plan_item.course_id` to `course.id`.

**Progress is computed, only the summary is stored.** Counts, remaining classes, next class and `canMark` are
calculated per request. Only `percent` and `status` are saved on the course.

---

## Endpoints

| Method | Path | Roles | Returns |
|--------|------|-------|---------|
| `GET` | `/api/learning/classes` | student, teacher, admin | Courses visible to the caller, with progress and `nextClass` |
| `GET` | `/api/learning/overview` | student, teacher, admin | Term plus role-specific stat tiles |
| `GET` | `/api/learning/plan/{courseId}` | student, teacher, admin | Every class of the course, 404 unknown, 403 not enrolled / not your course |
| `PUT` | `/api/learning/plan/items/{id}` | teacher (own), admin | Marks a class complete or not, recomputes the course percent |
| `GET` | `/api/learning/courses` | admin | All course records |
| `GET` | `/api/learning/courses/{id}` | admin | One course, 404 if absent |
| `POST` | `/api/learning/courses` | admin | 201 + `Location`, 409 duplicate, 400 missing id or name |
| `PUT` | `/api/learning/courses/{id}` | admin | Updated course, 404 if absent |
| `DELETE` | `/api/learning/courses/{id}` | admin | 204, 404 if absent, classes cascade |
| `GET` | `/api/learning/whoami` | any | The active profile's environment label |

The first four match the team's API contract (service.html). The `/courses` surface satisfies Phase 1's
"simple CRUD REST functionality" requirement.

Until Keycloak is wired in, the caller comes from two headers, `X-User` and `X-Role` (`student`, `teacher` or
`admin`). In dev, no headers means `admin1`. In prod, missing headers return 401.

---

## Layers

    LearningController -> REST, HTTP status mapping
    LearningService -> role scoping, progress maths, transactions, course CRUD
    Course/LessonPlanItem/StudySession/Term Repository -> Spring Data JPA
    Course, LessonPlanItem, StudySession, Term -> entities
    CallerResolver -> who is calling (stand-in for the Keycloak JWT)
    EnrollmentClient -> stub list now, REST call to enrollment-service later
    LearningDtos -> response shapes copied from the team contract

---

## Dev and prod profiles

    PROFILE=dev  docker compose up --build learning-service
    PROFILE=prod docker compose up --build learning-service

| | dev | prod |
|---|---|---|
| Schema | Flyway `V1` creates it, Hibernate validates | Flyway `V1` creates it, Hibernate validates |
| Seed data | Flyway `V2` (`db/dev`) | none |
| SQL logging | on | off |
| Caller headers | optional (defaults to `admin1`) | required |

`GET /api/learning/whoami` prints which profile is live, and the label comes from the config server
(`learning-service-dev.yml` / `learning-service-prod.yml`). The same settings are kept as fallback blocks in
`application.yml`, so the service still boots if the config server is down; the label then says
`local fallback`.

Run dev once, then switch to prod. For a clean prod demo, `docker compose down -v` first so no dev seed rows
remain.

---

## Tests

    newman run SchramlJay_Phase1_Postman.json -r cli,htmlextra

34 requests, 79 assertions: all four contract endpoints for the three roles, the config-service check,
the full `/courses` CRUD cycle, mark-complete and undo, and the 400, 401, 403, 404 and 409 errors.
The collection undoes its own changes, so it can be re-run.

---

## Phase 2

Phase 1 asks for entities, repository, service and CRUD controllers, so the following is deliberately out of
scope:

- **Keycloak JWT.** `CallerResolver` reads headers today. The JWT read replaces its body, and nothing else
  changes. This also needs the issuer URI and a shared role converter.
- **Live enrollment.** Switch from the stub to the REST call with `ENROLLMENT_MODE=rest` once
  enrollment-service runs.
- **Gateway and discovery.** Confirm whether the gateway strips the `/api/learning` prefix, and set
  `EUREKA_ENABLED` once a discovery service exists.
- **Real catalogue.** The seed has 7 courses and 189 classes; the finished system has 18 and 468.
- **Study-session writes.** `study_session` is read-only here.
- **Automated unit tests** beyond the Postman collection.
