---
phase: quick-260516-w7i
plan: 01
subsystem: backend/database
tags: [liquibase, maven, database-migration, qa-environment, spring-profiles]
dependency_graph:
  requires: []
  provides: [liquibase-maven-plugin-dev, liquibase-maven-plugin-qa, spring-profile-qa]
  affects: [prestamil-backend/pom.xml, prestamil-backend/.gitignore, spring-datasource-qa]
tech_stack:
  added: [liquibase-maven-plugin@4.27.0]
  patterns: [maven-profiles-for-credential-isolation, spring-profile-per-environment, gitignore-negation-pattern]
key_files:
  created:
    - prestamil-backend/liquibase-qa.properties.example
    - prestamil-backend/src/main/resources/application-qa.properties
    - prestamil-backend/liquibase-dev.properties (gitignored)
    - prestamil-backend/liquibase-qa.properties (gitignored)
  modified:
    - prestamil-backend/pom.xml
    - prestamil-backend/.gitignore
decisions:
  - "Plugin version 4.27.0 chosen to match liquibase-core version bundled with Spring Boot 3.2.5 BOM (Liquibase 4.x line)"
  - "Plugin declared ONLY under Maven profiles (dev/qa), NOT in default <build> — prevents accidental runs on mvn compile/install"
  - "spring.liquibase.enabled=false in application-qa.properties — QA migration is exclusively manual via mvn liquibase:update -Pqa"
  - "QA env var prefix QA_DB_* (vs DB_* for dev) prevents credential collision in developer shell"
  - "MariaDB driver version 3.3.3 pinned explicitly in plugin <dependencies> (plugin classpath is separate from project classpath)"
metrics:
  duration: "~15 minutes"
  completed: "2026-05-16"
  tasks_completed: 2
  files_created: 4
  files_modified: 2
verified: aprobado
---

# Phase quick-260516-w7i Plan 01: Liquibase Maven Plugin with dev/qa Profiles Summary

**One-liner:** Liquibase Maven Plugin 4.27.0 wired under isolated Maven profiles (dev/qa) with gitignored credential files and Spring QA profile using manual-only migration.

## What Was Built

### Task 1 — liquibase-maven-plugin in pom.xml (commit: 97b34d2)

Added `<liquibase-maven-plugin.version>4.27.0</liquibase-maven-plugin.version>` to `<properties>` and a `<profiles>` block with two profiles:

- **dev** profile: plugin reads `liquibase-dev.properties` (local MariaDB)
- **qa** profile: plugin reads `liquibase-qa.properties` (remote QA MariaDB)

Both profiles include the MariaDB driver as a plugin dependency (plugin classpath is isolated from project classpath). No credentials in `pom.xml`. No `<executions>` bound to any phase — plugin only runs on explicit user invocation.

### Task 2 — Property files, Spring QA profile, .gitignore (commit: c99ded7)

| File | Status | Purpose |
|------|--------|---------|
| `liquibase-dev.properties` | Created, gitignored | Local dev DB credentials (user fills password) |
| `liquibase-qa.properties` | Created, gitignored | QA remote DB credentials (user fills all fields) |
| `liquibase-qa.properties.example` | Created, committed | Template with safe placeholders |
| `application-qa.properties` | Created, committed | Spring QA profile — env vars + liquibase disabled |
| `.gitignore` | Modified | Added `liquibase-*.properties` + `!*.example` negation |

## Usage

### Run migrations against local dev DB

```bash
# 1. Edit liquibase-dev.properties with your local MariaDB credentials (one time)
# 2. Check status (read-only, safe):
cd prestamil-backend
mvn liquibase:status -Pdev

# 3. Apply pending changesets:
mvn liquibase:update -Pdev
```

### Run migrations against QA remote DB

```bash
# 1. Edit liquibase-qa.properties with real QA credentials (gitignored)
# 2. Check status first (always verify before applying):
cd prestamil-backend
mvn liquibase:status -Pqa

# 3. Apply pending changesets:
mvn liquibase:update -Pqa
```

### Start Spring Boot app against QA (without auto-migration)

```bash
SPRING_PROFILES_ACTIVE=qa \
QA_DB_URL=jdbc:mariadb://YOUR_QA_HOST:3306/YOUR_QA_DB \
QA_DB_USERNAME=your_user \
QA_DB_PASSWORD=your_password \
ENCRYPTION_SECRET=your_secret \
mvn spring-boot:run
```

Note: `spring.liquibase.enabled=false` in `application-qa.properties` — the app starts without running Liquibase. Migrations must be applied manually before starting the app if new changesets exist.

## Key Decisions

| Decision | Rationale |
|----------|-----------|
| Plugin version 4.27.0 | Matches `liquibase-core` from Spring Boot 3.2.5 BOM (Liquibase 4.x line). Using 5.x would break compatibility. |
| Plugin ONLY under profiles | Prevents accidental `mvn compile` from touching the database. Must be explicit: `mvn liquibase:update -P{dev|qa}`. |
| `spring.liquibase.enabled=false` in QA | QA migration is exclusively manual. If left enabled, app startup would re-introduce auto-migration, defeating the purpose of this plan. |
| `QA_DB_*` env var prefix | Allows dev and QA credentials side-by-side in shell/env without collision (dev uses `DB_*` from `.env`). |
| No `<executions>` in plugin | Deliberate — no phase binding means the plugin never runs as part of the normal build lifecycle. |

## Important Reminders

1. **Before using `-Pdev`:** Edit `liquibase-dev.properties` with your local MariaDB password (replace `CHANGE_ME`).
2. **Before using `-Pqa`:** Edit `liquibase-qa.properties` with real QA host, username, and password. This file is gitignored — use `liquibase-qa.properties.example` as template.
3. **Never commit** `liquibase-dev.properties` or `liquibase-qa.properties` — `.gitignore` prevents it, but be aware.
4. **Changelog path:** Both property files point to `src/main/resources/db/changelog/db.changelog-master.xml` (filesystem path, not classpath — required by the Maven plugin).

## Deviations from Plan

### Auto-fixed Issues

None — plan executed exactly as written.

**One deviation documented:** The `mariadb-java-client` dependency inside the plugin's `<dependencies>` block uses version `3.3.3` explicitly (not managed by Boot BOM, since the plugin classpath is isolated). The plan said to use the coordinate but didn't specify a version for the plugin-scoped dependency. Version 3.3.3 is the version in Spring Boot 3.2.5's dependency management, making it consistent.

## Known Stubs

None — no stub data patterns. The `liquibase-dev.properties` has `password=CHANGE_ME` but this is an intentional placeholder for local setup, not a stub that flows to UI rendering.

## Self-Check: PASSED

All files found on disk:
- prestamil-backend/pom.xml: FOUND
- prestamil-backend/liquibase-dev.properties: FOUND
- prestamil-backend/liquibase-qa.properties: FOUND
- prestamil-backend/liquibase-qa.properties.example: FOUND
- prestamil-backend/src/main/resources/application-qa.properties: FOUND

All commits found:
- 97b34d2 (Task 1 — pom.xml profiles): FOUND
- c99ded7 (Task 2 — property files + gitignore + QA spring profile): FOUND
