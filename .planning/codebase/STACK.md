# Technology Stack

**Analysis Date:** 2026-05-12

## Backend

**Language:** Java 21
**Framework:** Spring Boot 3.2.5
**Build Tool:** Maven (via `spring-boot-starter-parent` 3.2.5)
**Group ID:** `com.ignis` / Artifact: `prestamil` 0.0.1-SNAPSHOT
**Source:** `prestamil-backend/pom.xml`

### Spring Boot Starters

| Starter | Purpose |
|---|---|
| `spring-boot-starter-web` | REST API (embedded Tomcat) |
| `spring-boot-starter-data-jpa` | JPA/Hibernate ORM |
| `spring-boot-starter-security` | Authentication and authorization |
| `spring-boot-starter-validation` | Bean Validation (Jakarta) |
| `spring-boot-starter-actuator` | Health and metrics endpoints |
| `spring-boot-starter-cache` | Caching abstraction |
| `spring-boot-starter-test` | Testing (JUnit 5, Mockito) |
| `spring-session-jdbc` | JDBC-backed HTTP session store |

### Key Backend Libraries

| Library | Version | Purpose |
|---|---|---|
| `mapstruct` | 1.5.5.Final | DTO ↔ entity mapping via annotation processing |
| `lombok` | 1.18.30 | Boilerplate reduction (`@Data`, `@Slf4j`, `@RequiredArgsConstructor`) |
| `mariadb-java-client` | (managed by Boot) | MariaDB JDBC driver |
| `caffeine` | (managed by Boot) | In-process cache (used for `sucursal` cache, 100 entries, 30 min TTL) |
| `escpos-coffee` | 4.1.0 | ESC/POS thermal receipt printer support |
| `jasperreports` | 6.21.0 | PDF report generation (contrato template at `src/main/resources/jasper/`) |
| `barbecue` | 1.5-beta1 | Barcode generation (used alongside JasperReports) |

### Session Management

- Strategy: **stateful HTTP session** stored in MariaDB via `spring-session-jdbc`
- Session table schema: `src/main/resources/schema-mariadb.sql`
- Session timeout: **1800 seconds (30 minutes)**
- Cookie: `JSESSIONID`, `HttpOnly=true`, `SameSite=Lax`
- Single-session enforcement: previous sessions for the same principal are deleted on new login
- Config class: `src/main/java/com/ignis/prestamil/config/SessionConfig.java` (`@EnableJdbcHttpSession`)

### Security

- Spring Security 6 with **explicit SecurityContext save** (required in Spring Security 6)
- CSRF disabled (REST API with CORS-controlled stateful sessions)
- Password hashing: BCrypt
- Config class: `src/main/java/com/ignis/prestamil/config/SecurityConfig.java`

### Annotation Processing (Maven Compiler Plugin 3.8.1)

- `mapstruct-processor` 1.5.5.Final
- `lombok` 1.18.30
- `lombok-mapstruct-binding` 0.2.0

---

## Frontend

**Language:** TypeScript 5.8.3
**Framework:** Angular 20 (core packages `^20.3.15`)
**Template:** Datta Able Free Angular Admin Template v6.2.0 (CodedThemes)
**Package Manager:** npm
**Lockfile:** `package-lock.json` present
**Source:** `prestamil-frontend/package.json`

### Angular Core Packages

| Package | Version |
|---|---|
| `@angular/core` | ^20.3.15 |
| `@angular/common` | ^20.3.15 |
| `@angular/router` | ^20.3.15 |
| `@angular/forms` | ^20.3.15 |
| `@angular/animations` | ^20.3.15 |
| `@angular/cdk` | 20.0.4 |
| `@angular/platform-browser` | ^20.3.15 |
| `@angular/localize` | ^20.3.15 |

### Key Frontend Libraries

| Package | Version | Purpose |
|---|---|---|
| `@ng-bootstrap/ng-bootstrap` | 19.0.0 | Bootstrap UI components for Angular |
| `bootstrap` | 5.3.7 | CSS framework (SCSS source imported in `angular.json`) |
| `apexcharts` | 4.7.0 | Chart rendering engine |
| `ng-apexcharts` | 1.16.0 | Angular wrapper for ApexCharts |
| `ngx-scrollbar` | 18.0.0 | Custom scrollbar component |
| `rxjs` | ~7.8.2 | Reactive programming (BehaviorSubject, Observable) |
| `screenfull` | 6.0.2 | Fullscreen API |
| `zone.js` | ~0.15.1 | Angular change detection |
| `tslib` | 2.8.1 | TypeScript runtime helpers |
| `@popperjs/core` | 2.11.8 | Tooltip/dropdown positioning |

### Dev Tools

| Package | Version | Purpose |
|---|---|---|
| `@angular/cli` | 20.0.4 | Angular CLI |
| `@angular-devkit/build-angular` | ^20.3.13 | Build system |
| `eslint` | 9.29.0 | Linting |
| `@angular-eslint/*` | 20.1.1 | Angular-specific ESLint rules |
| `@typescript-eslint/*` | 8.35.0 | TypeScript ESLint rules |
| `prettier` | 3.6.1 | Code formatting |

### Build Configuration

- Builder: `@angular-devkit/build-angular:browser`
- Styles: Bootstrap SCSS + custom `src/styles.scss`
- Scripts: `apexcharts.min.js` (global)
- Output: `dist/`
- Production budget: 2 MB initial, 4 KB per component style
- Environment switching: `src/environments/environment.ts` → `environment.prod.ts` at build time
- TypeScript target: `es2022`, module: `es2022`

### npm Scripts

```bash
npm start           # ng serve (dev server on :4200)
npm run build       # ng build
npm run build-prod  # ng build --configuration production
npm test            # ng test
npm run lint        # ng lint
npm run lint:fix    # ng lint --fix
npm run prettier    # prettier --write ./src
```

---

## Database

**Type:** MariaDB (relational)
**Connection URL (dev):** `jdbc:mariadb://10.103.133.1:3306/CasaEmp_DEV`
**Driver:** `org.mariadb.jdbc.Driver`
**ORM:** Hibernate (via Spring Data JPA)
**Dialect:** `org.hibernate.dialect.MariaDBDialect`
**DDL:** `none` (schema managed manually; SQL in `src/main/resources/schema-mariadb.sql`)
**Naming Strategy:** `PhysicalNamingStrategyStandardImpl` (column names exactly as declared)
**SQL Logging:** enabled in dev (`spring.jpa.show-sql=true`, `format_sql=true`)

### Spring Session Tables (MariaDB)

- `SPRING_SESSION` — active sessions indexed by principal name
- `SPRING_SESSION_ATTRIBUTES` — serialized session attributes

---

## Infrastructure

**Server Port:** 8080 (backend) / 4200 (Angular dev)
**Containerization:** None detected (no Dockerfile or docker-compose files)
**CI/CD:** None detected (no `.github/`, no pipeline YAML files)

### Environment Configuration (Backend)

Config file: `prestamil-backend/src/main/resources/application.properties`

- `spring.datasource.url` — MariaDB connection string
- `spring.datasource.username` / `spring.datasource.password` — DB credentials (currently hardcoded in properties file)
- `spring.session.store-type=jdbc`
- `spring.session.timeout.in-seconds=1800`
- `server.servlet.session.cookie.http-only=true`
- `server.servlet.session.cookie.same-site=Lax`
- `server.port=8080`

### Environment Configuration (Frontend)

Config files: `prestamil-frontend/src/environments/environment.ts` and `environment.prod.ts`

- `apiUrl`: `http://localhost:8080` in both dev and prod (prod URL is a TODO placeholder)
- `production`: `true` in both (dev file sets `production: true` — likely an oversight)
- `appVersion`: pulled from `package.json` via `resolveJsonModule`

---

## Key Dependencies Summary

| Name | Version | Layer | Purpose |
|---|---|---|---|
| Java | 21 | Backend | Language runtime |
| Spring Boot | 3.2.5 | Backend | Application framework |
| Spring Security | (Boot-managed) | Backend | Auth and session management |
| Spring Session JDBC | (Boot-managed) | Backend | Stateful session persistence in DB |
| Hibernate/JPA | (Boot-managed) | Backend | ORM |
| MariaDB JDBC Driver | (Boot-managed) | Backend | Database connectivity |
| MapStruct | 1.5.5.Final | Backend | DTO mapping |
| Lombok | 1.18.30 | Backend | Code generation |
| Caffeine | (Boot-managed) | Backend | In-memory cache |
| JasperReports | 6.21.0 | Backend | PDF report generation |
| escpos-coffee | 4.1.0 | Backend | Thermal printer (ESC/POS) |
| barbecue | 1.5-beta1 | Backend | Barcode generation |
| Angular | ^20.3.15 | Frontend | SPA framework |
| TypeScript | 5.8.3 | Frontend | Language |
| RxJS | ~7.8.2 | Frontend | Reactive streams |
| Bootstrap | 5.3.7 | Frontend | CSS framework |
| ng-bootstrap | 19.0.0 | Frontend | Bootstrap Angular components |
| ApexCharts | 4.7.0 | Frontend | Charts |
| zone.js | ~0.15.1 | Frontend | Angular change detection |

---

*Stack analysis: 2026-05-12*
