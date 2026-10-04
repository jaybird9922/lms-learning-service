#!/usr/bin/env python3
"""Regenerates the Phase 1 Postman collection (repo root), run from learning-service/:

    python3 tools/gen_postman.py > ../SchramlJay_Phase1_Postman.json
"""
import json

BASE = "{{baseUrl}}"
items = []


def req(name, method, path, tests, user=None, role=None, body=None, desc=None):
    headers = []
    if user:
        headers.append({"key": "X-User", "value": user})
    if role:
        headers.append({"key": "X-Role", "value": role})
    r = {"method": method, "header": headers,
         "url": {"raw": BASE + path, "host": [BASE], "path": [p for p in path.strip("/").split("/")]}}
    if desc:
        r["description"] = desc
    if body is not None:
        headers.append({"key": "Content-Type", "value": "application/json"})
        r["body"] = {"mode": "raw", "raw": body}
    items.append({"name": name, "request": r,
                  "event": [{"listen": "test", "script": {"type": "text/javascript", "exec": tests}}]})


S, T, A, T2 = "{{student}}", "{{teacher}}", "{{admin}}", "{{otherTeacher}}"

req("1. Health check", "GET", "/actuator/health", [
    "pm.test('service is up', () => pm.response.to.have.status(200));",
    "pm.test('status is UP', () => pm.expect(pm.response.json().status).to.eql('UP'));"])

req("2. Config service reachable", "GET", "/api/learning/whoami", [
    "pm.test('returns 200', () => pm.response.to.have.status(200));",
    "const label = pm.response.text();",
    "console.log('Environment: ' + label);",
    "pm.test('label served by config-service', () => pm.expect(label).to.include('config-service'));"],
    desc="Returns the environment label. If it mentions config-service, the config server is genuinely being read (not the local fallback).")

req("3. No identity headers (dev: 200 as admin1, prod: 401)", "GET", "/api/learning/classes", [
    "pm.test('dev answers 200, prod answers 401', () => pm.expect(pm.response.code).to.be.oneOf([200, 401]));"],
    desc="Dev has a default caller (admin1); prod does not. Either result is correct for its profile.")

req("4. Invalid role header -> 400", "GET", "/api/learning/overview", [
    "pm.test('returns 400', () => pm.response.to.have.status(400));"], user=S, role="principal")

req("5. Overview - student", "GET", "/api/learning/overview", [
    "pm.test('returns 200', () => pm.response.to.have.status(200));",
    "const b = pm.response.json();",
    "pm.test('role is student', () => pm.expect(b.role).to.eql('student'));",
    "pm.test('term is present', () => pm.expect(b.term.name).to.be.a('string'));",
    "pm.test('4 tiles', () => pm.expect(b.stats).to.have.lengthOf(4));",
    "pm.test('tile labels', () => pm.expect(b.stats.map(s => s.label)).to.eql(['Enrolled Classes', 'Classes Completed', 'Next Class', 'Study Hours']));",
    "pm.test('completed tile carries progress', () => pm.expect(b.stats[1].progress).to.be.a('number'));"],
    user=S, role="student")

req("6. Overview - teacher", "GET", "/api/learning/overview", [
    "pm.test('returns 200', () => pm.response.to.have.status(200));",
    "const b = pm.response.json();",
    "pm.test('role is teacher', () => pm.expect(b.role).to.eql('teacher'));",
    "pm.test('tile labels', () => pm.expect(b.stats.map(s => s.label)).to.eql(['Courses Taught', 'Classes This Term', 'Classes Completed', 'Next Class']));"],
    user=T, role="teacher")

req("7. Overview - admin", "GET", "/api/learning/overview", [
    "pm.test('returns 200', () => pm.response.to.have.status(200));",
    "const b = pm.response.json();",
    "pm.test('role is admin', () => pm.expect(b.role).to.eql('admin'));",
    "pm.test('tile labels', () => pm.expect(b.stats.map(s => s.label)).to.eql(['Courses', 'Classes This Term', 'Classes Completed', 'Study Hours']));"],
    user=A, role="admin")

req("8. Classes - student (enrolled only)", "GET", "/api/learning/classes", [
    "pm.test('returns 200', () => pm.response.to.have.status(200));",
    "const rows = pm.response.json();",
    "pm.test('returns an array', () => pm.expect(rows).to.be.an('array'));",
    "pm.test('includes the sample course', () => pm.expect(rows.map(r => r.id)).to.include(pm.collectionVariables.get('courseId')));",
    "pm.test('student can never mark', () => pm.expect(rows.every(r => r.canMark === false)).to.be.true);",
    "pm.test('counts add up', () => pm.expect(rows.every(r => r.classes === r.classesCompleted + r.classesRemaining)).to.be.true);",
    "pm.collectionVariables.set('studentCourseCount', rows.length);"],
    user=S, role="student")

req("9. Classes - teacher (own courses only)", "GET", "/api/learning/classes", [
    "pm.test('returns 200', () => pm.response.to.have.status(200));",
    "const rows = pm.response.json();",
    "pm.test('only own courses', () => pm.expect(rows.every(r => r.teacherUsername === pm.collectionVariables.get('teacher'))).to.be.true);",
    "pm.test('teacher can mark own courses', () => pm.expect(rows.every(r => r.canMark === true)).to.be.true);"],
    user=T, role="teacher")

req("10. Classes - admin (all courses)", "GET", "/api/learning/classes", [
    "pm.test('returns 200', () => pm.response.to.have.status(200));",
    "const rows = pm.response.json();",
    "pm.test('admin sees at least what a student sees', () => pm.expect(rows.length).to.be.at.least(Number(pm.collectionVariables.get('studentCourseCount'))));",
    "pm.test('admin can mark', () => pm.expect(rows.every(r => r.canMark === true)).to.be.true);"],
    user=A, role="admin")

req("11. Plan - student, enrolled course", "GET", "/api/learning/plan/{{courseId}}", [
    "pm.test('returns 200', () => pm.response.to.have.status(200));",
    "const b = pm.response.json();",
    "pm.test('is the requested course', () => pm.expect(b.id).to.eql(pm.collectionVariables.get('courseId')));",
    "pm.test('items match the class count', () => pm.expect(b.items).to.have.lengthOf(b.classes));",
    "pm.test('items are in class order', () => pm.expect(b.items.map(i => i.classNumber)).to.eql(b.items.map((_, n) => n + 1)));",
    "pm.test('term is included', () => pm.expect(b.term.start).to.be.a('string'));",
    "pm.test('student cannot mark', () => pm.expect(b.canMark).to.be.false);"],
    user=S, role="student")

req("12. Plan - student, course not enrolled -> 403", "GET", "/api/learning/plan/ap-calc-ab", [
    "pm.test('returns 403', () => pm.response.to.have.status(403));"], user=S, role="student")

req("13. Plan - unknown course -> 404", "GET", "/api/learning/plan/no-such-course", [
    "pm.test('returns 404', () => pm.response.to.have.status(404));"], user=A, role="admin")

req("14. Plan - other teacher's course -> 403", "GET", "/api/learning/plan/{{courseId}}", [
    "pm.test('returns 403', () => pm.response.to.have.status(403));"], user=T2, role="teacher")

req("15. Plan - teacher (remember next class and percent)", "GET", "/api/learning/plan/{{courseId}}", [
    "pm.test('returns 200', () => pm.response.to.have.status(200));",
    "const b = pm.response.json();",
    "pm.test('teacher can mark', () => pm.expect(b.canMark).to.be.true);",
    "pm.test('course has a next class to mark', () => pm.expect(b.nextClass).to.not.be.null);",
    "pm.collectionVariables.set('itemId', b.nextClass.id);",
    "pm.collectionVariables.set('percentBefore', b.percent);",
    "pm.collectionVariables.set('completedBefore', b.classesCompleted);"],
    user=T, role="teacher",
    desc="Stores the id of the next uncompleted class so the PUT requests below have something to mark.")

req("16. Mark class complete - teacher", "PUT", "/api/learning/plan/items/{{itemId}}", [
    "pm.test('returns 200', () => pm.response.to.have.status(200));",
    "const b = pm.response.json();",
    "pm.test('item is completed', () => pm.expect(b.item.completed).to.be.true);",
    "pm.test('records who marked it', () => pm.expect(b.item.completedBy).to.eql(pm.collectionVariables.get('teacher')));",
    "pm.test('records when', () => pm.expect(b.item.completedAt).to.be.a('string'));",
    "pm.test('one more class done', () => pm.expect(b.completed).to.eql(Number(pm.collectionVariables.get('completedBefore')) + 1));",
    "pm.test('percent went up', () => pm.expect(b.coursePercent).to.be.above(Number(pm.collectionVariables.get('percentBefore'))));"],
    user=T, role="teacher", body='{\n  "completed": true\n}')

req("17. Student sees the change", "GET", "/api/learning/plan/{{courseId}}", [
    "pm.test('returns 200', () => pm.response.to.have.status(200));",
    "const b = pm.response.json();",
    "const it = b.items.find(i => i.id === Number(pm.collectionVariables.get('itemId')));",
    "pm.test('class shows as completed', () => pm.expect(it.completed).to.be.true);",
    "pm.test('course percent updated', () => pm.expect(b.percent).to.be.above(Number(pm.collectionVariables.get('percentBefore'))));"],
    user=S, role="student")

req("18. Mark class - student -> 403", "PUT", "/api/learning/plan/items/{{itemId}}", [
    "pm.test('returns 403', () => pm.response.to.have.status(403));"],
    user=S, role="student", body='{\n  "completed": false\n}')

req("19. Mark class - other teacher -> 403", "PUT", "/api/learning/plan/items/{{itemId}}", [
    "pm.test('returns 403', () => pm.response.to.have.status(403));"],
    user=T2, role="teacher", body='{\n  "completed": false\n}')

req("20. Mark class - missing body field -> 400", "PUT", "/api/learning/plan/items/{{itemId}}", [
    "pm.test('returns 400', () => pm.response.to.have.status(400));"],
    user=T, role="teacher", body="{}")

req("21. Mark class - unknown id -> 404", "PUT", "/api/learning/plan/items/999999", [
    "pm.test('returns 404', () => pm.response.to.have.status(404));"],
    user=A, role="admin", body='{\n  "completed": true\n}')

req("22. Undo - mark not complete (admin) and restore", "PUT", "/api/learning/plan/items/{{itemId}}", [
    "pm.test('returns 200', () => pm.response.to.have.status(200));",
    "const b = pm.response.json();",
    "pm.test('item is not completed', () => pm.expect(b.item.completed).to.be.false);",
    "pm.test('completedAt cleared', () => pm.expect(b.item.completedAt).to.be.null);",
    "pm.test('completedBy cleared', () => pm.expect(b.item.completedBy).to.be.null);",
    "pm.test('percent back to where it started', () => pm.expect(b.coursePercent).to.eql(Number(pm.collectionVariables.get('percentBefore'))));"],
    user=A, role="admin", body='{\n  "completed": false\n}')


# ---- /courses CRUD (admin only). Cleanup first so an aborted earlier run cannot cause a 409.
CRUD = '{\n  "id": "{{crudId}}",\n  "name": "Demo Course",\n  "gradeLevel": "Senior",\n  "teacher": "Dr John Anderson",\n  "teacherUsername": "janderson",\n  "color": "green"\n}'
CRUD2 = '{\n  "name": "Demo Course (renamed)",\n  "gradeLevel": "Junior",\n  "teacher": "Dr John Anderson",\n  "teacherUsername": "janderson",\n  "color": "red"\n}'

req("CRUD cleanup (ignore result)", "DELETE", "/api/learning/courses/{{crudId}}", [
    "pm.test('204 if a leftover existed, 404 if not', () => pm.expect(pm.response.code).to.be.oneOf([204, 404]));"],
    user=A, role="admin")

req("CREATE course", "POST", "/api/learning/courses", [
    "pm.test('returns 201', () => pm.response.to.have.status(201));",
    "const b = pm.response.json();",
    "pm.test('Location header points at the new course', () => pm.expect(pm.response.headers.get('Location')).to.include('/api/learning/courses/' + pm.collectionVariables.get('crudId')));",
    "pm.test('id and name saved', () => { pm.expect(b.id).to.eql(pm.collectionVariables.get('crudId')); pm.expect(b.name).to.eql('Demo Course'); });",
    "pm.test('percent and status start at defaults', () => { pm.expect(b.percent).to.eql(0); pm.expect(b.status).to.eql('not-started'); });"],
    user=A, role="admin", body=CRUD)

req("CREATE duplicate course -> 409", "POST", "/api/learning/courses", [
    "pm.test('returns 409', () => pm.response.to.have.status(409));"],
    user=A, role="admin", body=CRUD)

req("CREATE course without a name -> 400", "POST", "/api/learning/courses", [
    "pm.test('returns 400', () => pm.response.to.have.status(400));"],
    user=A, role="admin", body='{\n  "id": "no-name-course"\n}')

req("CREATE course as teacher -> 403", "POST", "/api/learning/courses", [
    "pm.test('returns 403', () => pm.response.to.have.status(403));"],
    user=T, role="teacher", body=CRUD)

req("READ all courses (admin)", "GET", "/api/learning/courses", [
    "pm.test('returns 200', () => pm.response.to.have.status(200));",
    "const rows = pm.response.json();",
    "pm.test('includes the new course', () => pm.expect(rows.map(r => r.id)).to.include(pm.collectionVariables.get('crudId')));",
    "pm.test('sorted by name', () => pm.expect(rows.map(r => r.name)).to.eql([...rows.map(r => r.name)].sort((a, b) => a.localeCompare(b))));"],
    user=A, role="admin")

req("READ all courses as student -> 403", "GET", "/api/learning/courses", [
    "pm.test('returns 403', () => pm.response.to.have.status(403));"],
    user=S, role="student")

req("READ one course", "GET", "/api/learning/courses/{{crudId}}", [
    "pm.test('returns 200', () => pm.response.to.have.status(200));",
    "pm.test('is the requested course', () => pm.expect(pm.response.json().name).to.eql('Demo Course'));"],
    user=A, role="admin")

req("UPDATE course", "PUT", "/api/learning/courses/{{crudId}}", [
    "pm.test('returns 200', () => pm.response.to.have.status(200));",
    "const b = pm.response.json();",
    "pm.test('fields updated', () => { pm.expect(b.name).to.eql('Demo Course (renamed)'); pm.expect(b.gradeLevel).to.eql('Junior'); pm.expect(b.color).to.eql('red'); });",
    "pm.test('id unchanged', () => pm.expect(b.id).to.eql(pm.collectionVariables.get('crudId')));"],
    user=A, role="admin", body=CRUD2)

req("UPDATE unknown course -> 404", "PUT", "/api/learning/courses/no-such-course", [
    "pm.test('returns 404', () => pm.response.to.have.status(404));"],
    user=A, role="admin", body=CRUD2)

req("DELETE course", "DELETE", "/api/learning/courses/{{crudId}}", [
    "pm.test('returns 204', () => pm.response.to.have.status(204));"],
    user=A, role="admin")

req("READ deleted course -> 404", "GET", "/api/learning/courses/{{crudId}}", [
    "pm.test('returns 404', () => pm.response.to.have.status(404));"],
    user=A, role="admin")

col = {
    "info": {
        "name": "LMS - learning-service (Phase 1)",
        "description": ("Tests for learning-service (port 8105): the four endpoints from service.html, "
                        "role scoping, the admin /courses CRUD cycle, and the 400/401/403/404/409 cases.\n\nRun the whole collection in order: request 15 "
                        "stores the id of the next class and request 22 undoes the change, so it is safe to re-run.\n\n"
                        "Roles are sent as X-User / X-Role headers (stand-in for Keycloak). Run headless: "
                        "newman run SchramlJay_Phase1_Postman.json -r cli,htmlextra"),
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
    "variable": [
        {"key": "baseUrl", "value": "http://localhost:8105"},
        {"key": "courseId", "value": "ap-precalc"},
        {"key": "student", "value": "student1"},
        {"key": "teacher", "value": "janderson"},
        {"key": "otherTeacher", "value": "ppatel"},
        {"key": "admin", "value": "admin1"},
        {"key": "crudId", "value": "ap-demo-course"},
        {"key": "itemId", "value": ""},
        {"key": "percentBefore", "value": ""},
        {"key": "completedBefore", "value": ""},
        {"key": "studentCourseCount", "value": ""}],
    "item": items}
import re
n = 0
for it in col["item"]:
    n += 1
    it["name"] = f"{n}. " + re.sub(r"^\d+\.\s*", "", it["name"])
print(json.dumps(col, indent=2, ensure_ascii=False))
