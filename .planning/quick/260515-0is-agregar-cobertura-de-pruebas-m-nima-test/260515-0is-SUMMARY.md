---
phase: quick-260515-0is
plan: 01
subsystem: backend-tests
tags: [testing, mockito, h2, data-jpa-test, unit-test, integration-test]
dependency-graph:
  requires: [quick-260514-x0j, quick-260515-04c]
  provides: [test-scaffold-for-fulltext-phase]
  affects: [prestamil-backend/pom.xml, prestamil-backend/src/test/]
tech-stack:
  added: [H2 in-memory DB (test scope), maven-surefire-plugin *IT include]
  patterns: [MockitoExtension unit tests, DataJpaTest H2 integration tests, TestEntityManager entity persistence]
key-files:
  created:
    - prestamil-backend/src/test/resources/application-test.properties
    - prestamil-backend/src/test/java/com/ignis/prestamil/service/TurnoServiceTest.java
    - prestamil-backend/src/test/java/com/ignis/prestamil/service/UsuarioServiceTest.java
    - prestamil-backend/src/test/java/com/ignis/prestamil/repository/TurnoRepositoryIT.java
    - prestamil-backend/src/test/java/com/ignis/prestamil/repository/ClienteRepositoryIT.java
  modified:
    - prestamil-backend/pom.xml
decisions:
  - H2 MariaDB-compat mode selected for @DataJpaTest to match production JPQL
  - SecurityContext mocked per-test (not @BeforeEach) to avoid UnnecessaryStubbingException
  - Surefire *IT.java include added because Maven excludes *IT by default
  - Usuario stub in TurnoRepositoryIT persists full nullable=false fields manually (not reflection)
metrics:
  duration: ~15 minutes
  completed: 2026-05-15
  tasks: 3
  files: 6
---

# Phase quick-260515-0is Plan 01: Minimum Test Coverage Summary

**One-liner:** H2-backed test scaffold with Mockito unit tests for TurnoService/UsuarioService and @DataJpaTest integration tests for TurnoRepository/ClienteRepository — 26 tests, 0 failures.

## Test Counts per Class

| Class | Type | Tests | Status |
|-------|------|-------|--------|
| TurnoServiceTest | Unit (Mockito) | 8 | PASS |
| UsuarioServiceTest | Unit (Mockito) | 7 | PASS |
| TurnoRepositoryIT | @DataJpaTest (H2) | 3 | PASS |
| ClienteRepositoryIT | @DataJpaTest (H2) | 5 | PASS |
| PrestamilApplicationTests | @SpringBootTest (MariaDB) | 1 | PASS |
| SessionTimeoutListenerTest | Pre-existing | 2 | PASS |
| **Total** | | **26** | **ALL PASS** |

## Test Execution Summary

```
mvn test
Tests run: 26, Failures: 0, Errors: 0, Skipped: 0
Total time: ~30s
BUILD SUCCESS
```

Surefire total time: ~30 seconds. The bulk (~13s) is from `PrestamilApplicationTests` booting the full Spring context against MariaDB.

## Commits

| Hash | Description |
|------|-------------|
| 1cd9b0a | chore: add H2 test dependency and application-test.properties |
| a3c1428 | test: unit tests for TurnoService and UsuarioService (Mockito) |
| 21c128b | test: @DataJpaTest integration tests for TurnoRepository and ClienteRepository |
| 72ed6a0 | chore: configure Surefire to include *IT.java test classes |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] SecurityContext stubs in @BeforeEach caused UnnecessaryStubbingException**
- **Found during:** Task 2 — first test run
- **Issue:** `TurnoServiceTest` stubbed `auth.getName()` and `ctx.getAuthentication()` in `@BeforeEach`, but `obtenerTurnoActivo` and `cerrarTurno` tests don't call `iniciarTurno` (which reads SecurityContextHolder). Mockito strict mode fails.
- **Fix:** Moved SecurityContext mock setup to a `mockSecurityContext(String)` helper called explicitly in the 3 tests that need it (`iniciarTurno_*`).
- **Files modified:** `TurnoServiceTest.java`
- **Commit:** a3c1428

**2. [Rule 1 - Bug] *IT.java files not discovered by mvn test (Surefire default excludes them)**
- **Found during:** Task 3 verification — full `mvn test` showed only 18 tests (skipping the 8 IT tests)
- **Issue:** Maven Surefire default pattern includes `**/*Test.java` and `**/*Tests.java` but NOT `**/*IT.java`. Files named `*IT.java` are by Maven convention run by `maven-failsafe-plugin` in the `verify` lifecycle phase.
- **Fix:** Added `maven-surefire-plugin` configuration in `pom.xml` with explicit includes for `*Test.java`, `*Tests.java`, and `*IT.java` so all tests run under `mvn test`.
- **Files modified:** `prestamil-backend/pom.xml`
- **Commit:** 72ed6a0

### Strategy Notes

**Usuario stub in TurnoRepositoryIT:** The Usuario entity has 12+ nullable=false columns plus a mandatory FK to Rol. Approach used was JPA path (persist Rol first, then Usuario with all required fields set via setters). No reflection or native SQL was needed.

**Direccion stub in ClienteRepositoryIT:** Direccion requires `tipoDireccion` (enum), `calle`, `numeroExterior`, `colonia`, `ciudad`, `estado`, `codigoPostal`, `fechaRegistro`. All set in `persistMinimalDireccion()` helper. Each test gets a fresh Direccion (unique instances) to avoid unique constraint conflicts between tests.

## Production Code Changes

None. Only `src/test/**` files were created. The only `src/main` file change was `pom.xml` — adding H2 as `<scope>test</scope>` (no production runtime impact) and Surefire configuration.

## Known Stubs

None. All test data is minimal but valid — no hardcoded empty values flow to production code paths.

## Self-Check: PASSED

All artifact files exist. All 4 commit hashes present in git log. No MATCH/AGAINST syntax in test files. Full `mvn test` reports 26 tests, 0 failures, BUILD SUCCESS.
