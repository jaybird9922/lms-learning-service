# learning-service

Port **8105**, database **learningdb**, base path **/api/learning**. Owner: Jay Bruhn Schraml.
Built from the shared `lms-skeleton` template. Needs only `postgres` and `config-service`
(both start automatically). No gateway, Eureka, Keycloak or enrollment-service required.

## Run it

From the repo root (the folder with `docker-compose.yml`):

    docker compose up --build learning-service

Three containers come up: `postgres`, `config-service` (8888) and `learning-service` (8105). The service
waits for the config server's health check.

    curl localhost:8105/actuator/health
    curl localhost:8105/api/learning/whoami
    curl localhost:8105/api/learning/classes
    docker compose exec postgres psql -U lms -d learningdb -c '\dt'

`whoami` should say `DEV - served by config-service ...`. If it says `local fallback`, the config server was
not reachable and the service is running on its built-in settings.

## Endpoints

| Method | Path | Roles | Notes |
|---|---|---|---|
| GET | `/api/learning/classes` | S T A | student: enrolled courses, teacher: own, admin: all. Counts, percent, status, `nextClass`, `canMark`. |
| GET | `/api/learning/overview` | S T A | term + stat tiles; tiles differ per role. |
| GET | `/api/learning/plan/{courseId}` | S T A | every class of the course this term. 404 unknown, 403 not enrolled / not your course. |
| PUT | `/api/learning/plan/items/{id}` | T A | body `{"completed": true\|false}`. Teacher on own course, or admin. Recomputes percent and status. |
| GET | `/api/learning/courses` | A | Phase 1 CRUD on the course records |
| GET | `/api/learning/courses/{id}` | A | 404 if absent |
| POST | `/api/learning/courses` | A | 201 + `Location`, 409 duplicate id, 400 missing id/name |
| PUT | `/api/learning/courses/{id}` | A | 404 if absent. `percent` and `status` are derived, not settable. |
| DELETE | `/api/learning/courses/{id}` | A | 204, 404 if absent. The database cascades to the course's classes. |
| GET | `/api/learning/whoami` | any | the active environment label |

## Who is calling (stand-in for Keycloak)

    curl -H 'X-User: janderson' -H 'X-Role: teacher' localhost:8105/api/learning/classes

- **dev**: no headers means `admin1` / admin, so plain `curl` works.
- **prod**: no default, so a request without both headers gets **401**. A bad role gets **400**.
- When Keycloak arrives, change only `CallerResolver.resolve()` to read the JWT.

## Enrollment (which courses a student is in)

enrollment-service owns this (`GET /api/enrollment/state` returns `{"enrolled": [...]}`).

- **stub (default)**: lists in `application.yml` under `lms.enrollment.stub.<username>`.
- **rest**: add to the `learning-service` block in `docker-compose.yml`:

      ENROLLMENT_MODE: rest
      ENROLLMENT_URL: http://enrollment-service:8104

## Config service and profiles

`application.yml` imports `optional:configserver:${CONFIG_URL:http://config-service:8888}`. The config server
serves these files from `config-service/src/main/resources/config/`:

| File | Purpose |
|---|---|
| `learning-service.yml` | shared by every profile |
| `learning-service-dev.yml` | dev: Flyway schema + seed, SQL logging, default caller |
| `learning-service-prod.yml` | prod: Flyway schema only, quiet logging, no default caller |

The same settings are kept as fallback blocks in `application.yml`, so the service still boots if the config
server is down. Config files are baked into the config-service image: after editing one, run
`docker compose up --build learning-service` again.

    PROFILE=dev  docker compose up --build learning-service
    PROFILE=prod docker compose up --build learning-service

## Database (Flyway)

Flyway builds the schema; Hibernate only validates that the entities match it (`ddl-auto: validate`).

| File | Loaded by | What it does |
|---|---|---|
| `src/main/resources/db/migration/V1__schema.sql` | dev and prod | 4 tables, foreign key, `text` columns, defaults |
| `src/main/resources/db/dev/V2__seed.sql` | **dev only** | seed data (generated, do not hand-edit) |

To change the seed, edit `tools/gen_seed.py` and run:

    python3 tools/gen_seed.py > src/main/resources/db/dev/V2__seed.sql

Seed: term `q1-2026`, 7 courses x 27 classes, first 14-15 classes per course complete, 16 study sessions.

Flyway runs each migration once and records it, so after changing a migration that already ran, wipe the volume:
`docker compose down -v` (deletes every database in your local compose). Never edit `V1` after teammates have
run it; add `V3__...sql`.

## Postman

`SchramlJay_Phase1_Postman.json` (repo root): 34 requests, 79 assertions. Covers all four endpoints, the three
roles, the config-service check, the admin `/courses` CRUD cycle, and the 400/401/403/404/409 cases. Safe to
re-run (it undoes its own changes). Regenerate from this folder with
`python3 tools/gen_postman.py > ../SchramlJay_Phase1_Postman.json`.

    newman run SchramlJay_Phase1_Postman.json -r cli,htmlextra

## Troubleshooting

- **`whoami` says `local fallback`**: config-service wasn't reachable. `docker compose ps` should show it
  healthy; run `docker compose up --build learning-service` so it is rebuilt with the learning-service files.
- **`Found non-empty schema(s) "public" but no schema history table`**: the database was built by an older
  Hibernate version. `docker compose down -v`, then start again.
- **`Schema-validation: wrong column type` / `missing table`**: an entity no longer matches a migration. Fix the
  entity or add a new `V3__` migration; do not edit V1.
- **Seed rows still there in prod**: the volume was seeded by an earlier dev run. `docker compose down -v` first.
- **Port already in use**: `docker compose ps` to see what's running, `docker compose down` to stop everything.

## Known gaps / assumptions

- Course ids, teacher usernames and the term id (`q1-2026`) come from the team's service.html. Confirm they match
  enrollment-service's data.
- Seed has 7 courses and 189 classes; the finished system has 18 courses and 468 classes.
- Times use the container's clock (UTC in the Docker image).
