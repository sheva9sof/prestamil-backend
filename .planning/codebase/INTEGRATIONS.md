# Integrations & External Services

**Analysis Date:** 2026-05-12

---

## Internal Integration (Frontend ↔ Backend)

### Communication Protocol

REST over HTTP. The Angular SPA calls the Spring Boot API directly.

### Base URL

Defined in Angular environment files:

- Dev: `http://localhost:8080` (`src/environments/environment.ts`)
- Prod: `http://localhost:8080` (`src/environments/environment.prod.ts`) — TODO placeholder, not yet set to a real production URL

All services reference `environment.apiUrl` at runtime. Example:
```typescript
// src/app/prestamil/core/services/turno.service.ts
private readonly API = `${environment.apiUrl}/api/turnos`;
```

### API URL Structure

| Prefix | Visibility | Description |
|---|---|---|
| `/auth/login` | Public | Session login |
| `/auth/logout` | Protected | Session logout (Spring Security) |
| `/auth/stream/**` | Public | SSE stream endpoints |
| `/api/**` | Protected | All business endpoints |

### Authentication Mechanism

**Stateful session cookies (JSESSIONID — HttpOnly).**

- The frontend sends `withCredentials: true` on every backend request, enabling the browser to attach the `JSESSIONID` cookie automatically.
- No JWT tokens are used anywhere in the active codebase (the `JwtAuthenticationFilter.java` file is present but empty — 1 line).
- On login, the backend creates a session in MariaDB (via Spring Session JDBC), issues a `Set-Cookie: JSESSIONID=...` response, and the frontend stores only user display data (username, name, role ID) in `localStorage`. No token is stored.

### Angular HTTP Interceptors

Two interceptors registered globally in `src/app/app-config.ts`:

**1. `CredentialsInterceptor`** (`src/app/prestamil/core/interceptors/credentials.interceptor.ts`)
- Automatically clones every request targeting `environment.apiUrl` and sets `withCredentials: true`.
- Ensures `JSESSIONID` cookie is sent on cross-origin requests.

**2. `AuthErrorInterceptor`** (`src/app/prestamil/core/interceptors/auth-error.interceptor.ts`)
- Catches HTTP 401, 403, or 440 responses from backend requests (excluding login/logout endpoints).
- If the user is currently authenticated, calls `AuthService.handleSessionInvalidation()` which clears local session state and redirects to `/login`.

### CORS Configuration (Backend)

Configured in `src/main/java/com/ignis/prestamil/config/CorsConfig.java`:

- Allowed origins: `http://localhost:4200` only
- Allowed methods: `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`
- Allowed headers: `*`
- `allowCredentials: true` (required for cookie-based auth to work cross-origin)
- Max age: 3600 seconds

**Note:** The dev environment uses IP `192.168.50.15` for network access (mentioned in `application.properties` comments). Accessing from `localhost:4200` against a backend on a different IP/host causes `SameSite=Lax` cookies to not be sent by the browser. The workaround documented in `application.properties` is to always access from `http://192.168.50.15:4200` or configure an Angular proxy.

### Key API Endpoints (Frontend Service References)

| Endpoint | Method | Service File |
|---|---|---|
| `/auth/login` | POST | `auth.service.ts` |
| `/auth/logout` | POST | `auth.service.ts` (also Spring Security at `/api/auth/logout`) |
| `/auth/stream/logout?username=X` | GET (SSE) | `auth-stream.service.ts` |
| `/api/usuarios/me` | GET | `auth.service.ts` |
| `/api/usuarios/{id}/cambiar-password` | POST | `auth.service.ts` |
| `/api/turnos/iniciar` | POST | `turno.service.ts` |
| `/api/turnos/cerrar/{id}` | POST | `turno.service.ts` |
| `/api/turnos/activo` | GET | `turno.service.ts` |

---

## Real-time / Events

### Server-Sent Events (SSE)

The backend exposes an SSE stream for push notifications to authenticated clients. This is the only real-time communication mechanism in the project.

**Backend endpoint:** `GET /auth/stream/logout?username={username}`
**Backend controller:** `src/main/java/com/ignis/prestamil/controller/LogoutStreamController.java`
**Frontend service:** `src/app/prestamil/core/services/auth-stream.service.ts`

**Implementation:** `SseEmitter` (Spring MVC) stored in a `ConcurrentHashMap<String, SseEmitter>` keyed by username. One emitter per connected user.

**Events emitted:**

| Event Name | Trigger | Frontend Behavior |
|---|---|---|
| `force-logout` | A new login for the same username invalidates the prior session | Clears localStorage, redirects to `/login` with message "Sesión revocada" |
| `turno-cerrado` | `TurnoController.cerrarTurno()` — broadcast to ALL connected users | Clears localStorage, redirects to `/login` with message "El turno ha sido cerrado" |

**Connection lifecycle:**
- `AuthStreamService.connect(username)` is called after successful login and on app startup if a session exists.
- `AuthStreamService.disconnect()` is called on logout.
- Reconnection uses exponential backoff (base 2s, max 5 attempts): `2s → 4s → 8s → 16s → 32s`.
- Connection uses `withCredentials: true` on `EventSource` for cookie transport.

**No WebSockets or polling** are used anywhere in the codebase.

---

## Data Sources

### Primary Database

- **Type:** MariaDB (relational)
- **Dev host:** `10.103.133.1:3306`
- **Dev database:** `CasaEmp_DEV`
- **Credentials:** Set in `prestamil-backend/src/main/resources/application.properties` (hardcoded, not externalized via env vars)
- **Access:** Spring Data JPA repositories in `src/main/java/com/ignis/prestamil/repository/`

### Session Store

- **Type:** MariaDB (same database instance as above)
- **Tables:** `SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES`
- **Schema:** `src/main/resources/schema-mariadb.sql`
- **Initialization:** `spring.session.jdbc.initialize-schema=embedded` (auto-creates tables only on embedded DBs — must be created manually on MariaDB using the provided SQL script)

### In-Memory Cache

- **Provider:** Caffeine (local, in-process)
- **Config:** `src/main/java/com/ignis/prestamil/config/CacheConfig.java`
- **Caches defined:** `sucursal` (max 100 entries, expires 30 min after write)
- **No distributed cache** (Redis, Memcached, etc.) is used.

### File Storage

- No external file storage (S3, GCS, etc.) detected.
- JasperReports compiled templates are bundled as classpath resources: `src/main/resources/jasper/contrato.jasper` (compiled) and `contrato.jrxml` (source).

---

## External Services

### Hardware Integration

**Thermal Printer (ESC/POS)**
- Library: `escpos-coffee` 4.1.0
- Purpose: Print loan contracts or receipts directly to a thermal POS printer
- Frontend page: `src/app/prestamil/pages/hardware/hardware.component.ts`
- No cloud service — communicates directly with locally-attached hardware.

**Barcode Generation**
- Library: `barbecue` 1.5-beta1
- Purpose: Generate barcodes embedded in JasperReports PDF output
- No external barcode service.

### Report Generation

- Library: JasperReports 6.21.0 (local, classpath)
- Template: `src/main/resources/jasper/contrato.jasper` (loan contract PDF)
- No external reporting service (e.g., Jaspersoft Server, BIRT, etc.)

### No Third-Party Cloud APIs Detected

The following are **not** present in the codebase:

- No payment processors (Stripe, PayPal, Conekta, etc.)
- No email/SMS providers (SendGrid, Twilio, SES, etc.)
- No OAuth providers (Google, Auth0, Okta, etc.)
- No cloud storage (S3, GCS, Azure Blob, etc.)
- No analytics or monitoring services (Datadog, Sentry, New Relic, etc.)
- No CDN or asset delivery service

---

## CI/CD & Deployment

- No Dockerfile, docker-compose, or container configuration detected.
- No `.github/workflows/`, Jenkins, GitLab CI, or other pipeline configuration detected.
- Deployment approach is not codified; the backend runs as a Spring Boot fat JAR on port 8080, and the frontend is served as static files from the `dist/` build output.

---

*Integration audit: 2026-05-12*
