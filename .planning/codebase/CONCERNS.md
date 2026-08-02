# Concerns & Risks

**Analysis Date:** 2026-05-22

---

## Security Concerns

**Hardcoded database credentials in source-controlled file:**
- Risk: Production/dev DB password `$IgnisD3v_2025` and username `admin` are committed plaintext in `prestamil-backend/src/main/resources/application.properties` (lines 6-7). This file is tracked by git and already has commit history referencing it (`deee305 eliminar ip personal` suggests a past IP was removed, but credentials remain).
- Current mitigation: None. No `.gitignore` excluding application.properties, no secrets management.
- Recommendation: Move all datasource credentials to environment variables (`SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`) and add a `.env.example` with placeholder values.

**Hardcoded encryption secret in source code:**
- Risk: The AES encryption key `$L@v@yS3c@Ignis2023_` is hardcoded as a string literal in `prestamil-backend/src/main/java/com/ignis/prestamil/util/Encryptor.java` (line 21). This key derives all password encryption. Anyone with repository access can decrypt every stored password.
- Files: `prestamil-backend/src/main/java/com/ignis/prestamil/util/Encryptor.java`
- Recommendation: Externalize to environment variable; rotate the key and re-encrypt all stored passwords.

**Weak password encryption scheme (AES/ECB, reversible):**
- Risk: Passwords are stored as reversible AES/ECB ciphertext. ECB mode is deterministic (same plaintext → same ciphertext) and exposes patterns. The login flow decrypts the stored password and compares in plaintext (`encryptor.decrypt(usuario.getPassword())`). This is fundamentally weaker than a proper one-way hash.
- Files: `prestamil-backend/src/main/java/com/ignis/prestamil/util/Encryptor.java`, `prestamil-backend/src/main/java/com/ignis/prestamil/service/UsuarioService.java` (lines 127-129, 238-239)
- Impact: A DB compromise exposes all user passwords. BCryptPasswordEncoder is already a dependency in `SecurityConfig.java` but is not used for actual user passwords.
- Recommendation: Migrate to BCrypt (already declared as `@Bean` in `SecurityConfig.java`). Requires a one-time migration script.

**Postman/Insomnia API collections committed to main source tree:**
- Risk: `prestamil-backend/src/main/resources/api-clients/` contains three API client config files that likely include environment URLs or test credentials. These ship inside the compiled JAR.
- Files: `prestamil-backend/src/main/resources/api-clients/prestamil-insomnia-session-local-v1.json`, `prestamil-backend/src/main/resources/api-clients/prestamil-postman-environment-local-v1.json`, `prestamil-backend/src/main/resources/api-clients/prestamil-postman-session-local-v1.json`
- Recommendation: Move to a non-classpath location (e.g., `docs/api-clients/`) or a separate repository.

**CSRF disabled with only Lax cookie SameSite:**
- Risk: CSRF protection is disabled (`AbstractHttpConfigurer::disable`) in `SecurityConfig.java` (line 49). The session cookie uses `SameSite=Lax` (not `None+Secure`). Comments in `application.properties` (lines 23-28) acknowledge that cross-origin requests (e.g., `localhost:4200` → `192.168.50.15:8080`) may not send the cookie at all, which is currently worked around by accessing both app and API from the same IP. This is fragile and creates operational confusion.
- Recommendation: Enable CSRF or add `Origin` header validation for state-mutating endpoints. In production, use HTTPS with `SameSite=None; Secure`.

**SSE endpoint exposes username in plain URL query parameter:**
- Risk: `GET /auth/stream/logout?username=<username>` is an unauthenticated SSE endpoint (permitted via `.requestMatchers("/auth/stream/**").permitAll()`). Any client that knows a username can subscribe to their logout stream, effectively receiving notifications about that user's session lifecycle.
- Files: `prestamil-backend/src/main/java/com/ignis/prestamil/controller/LogoutStreamController.java`, `prestamil-backend/src/main/java/com/ignis/prestamil/config/SecurityConfig.java` (line 66)
- Recommendation: Require authentication for the SSE endpoint, or use a short-lived, opaque token instead of the username as the identifier.

**No role-based access control enforcement on API endpoints:**
- Risk: All authenticated endpoints are protected by `anyRequest().authenticated()` only. There is no `@PreAuthorize`, `@Secured`, or role-based rule on any controller method. Any logged-in user can call any endpoint (e.g., `DELETE /api/usuarios/{id}`, `PUT /api/usuarios/{id}/cambiar-password` for any user ID).
- Files: All controllers under `prestamil-backend/src/main/java/com/ignis/prestamil/controller/`
- Impact: A regular cashier user can delete or modify administrator accounts.
- Recommendation: Add method-level security with `@EnableMethodSecurity` and `@PreAuthorize("hasRole('ADMIN')")` on sensitive operations.

**Password change endpoint allows changing any user's password without ownership check:**
- Risk: `PUT /api/usuarios/{id}/cambiar-password` accepts any integer ID. The service validates the current password but not whether the authenticated user is the owner. Any authenticated user knowing another user's current password can change it.
- Files: `prestamil-backend/src/main/java/com/ignis/prestamil/controller/UsuarioController.java` (lines 102-114), `prestamil-backend/src/main/java/com/ignis/prestamil/service/UsuarioService.java` (lines 233-259)

~~**`session_token` column on Usuario is unused dead code:**~~
RESOLVED 2026-05-15 (260515-04c) — columna eliminada vía changeset 005-drop-session-token-usuarios.sql. Ya no existe en la entidad ni en la DB.

**`show-sql=true` and `format_sql=true` are enabled:**
- Risk: All Hibernate SQL queries (including queries containing user data lookups) are printed to the application log in production-bound configuration.
- Files: `prestamil-backend/src/main/resources/application.properties` (lines 11, 13)
- Recommendation: Set both to `false` and move to a profile-specific `application-dev.properties`.

---

## Incomplete Features / TODOs

~~**Avaluos feature — mock UI funcional, backend pendiente:**~~
ACTUALIZADO 2026-05-22 (260522-h4a): Fase A backend completa. El componente ha sido renombrado a `avaluo.component.ts` (ruta `/avaluos`). Frontend conectado a APIs reales: `ClienteService.search()`, `PrendaService.getValoresPrenda()`, `ContratoService.crearContrato()`. Entidades JPA `Contrato`, `PartidaContrato`, `MovimientoContrato` implementadas. 5 endpoints REST operativos (POST/GET). Pendiente: PDF, refrendos, finiquitos (Fase B/C).

**Avaluos — decisiones pendientes de negocio:**
- ¿El campo "Libre avalúo" permite al valuador sobreescribir el precio calculado por el sistema?
- ¿Un contrato puede tener prendas de distintos tipos (ej: una alhaja + un celular en el mismo contrato)?
- ¿El campo Beneficiario es obligatorio o siempre opcional?
- ¿Refrendos y Finiquitos entran por esta misma pantalla o por una pantalla separada?
- ¿Existe catálogo de precios de referencia para electrónicos o el valuador decide a criterio propio?

**Hardware component is an empty shell:**
- `HardwareComponent` has an empty constructor, no template logic, and no service integration. The page exists in the route but does nothing.
- Files: `prestamil-frontend/src/app/prestamil/pages/hardware/hardware.component.ts`
- The `escpos-coffee` library (ESC/POS thermal printer SDK) is declared in `pom.xml` but `PrinterService` only has a stub `printTicket` that calls `System.out.println`.
- Files: `prestamil-backend/src/main/java/com/ignis/prestamil/service/PrinterService.java`

**~~Parametros Préstamo component is an empty shell:~~** RESOLVED 2026-05-16 (quick task 260516-mns) — implementado como vista solo lectura que muestra parámetros agrupados por plazo activo desde el backend usando `forkJoin`. Usa `inject(PlazoService)` style, standalone component.
- Files: `prestamil-frontend/src/app/prestamil/pages/configuracion/parametros-prestamo/parametros-prestamo.component.ts`

**Production API URL is localhost — TODO comment confirms it:**
- `prestamil-frontend/src/environments/environment.prod.ts` has `apiUrl: 'http://localhost:8080'` with a comment `// TODO: Cambiar por la URL de producción cuando esté lista`. A production build currently points to localhost.

**`ContratoTestMain` is a manual test utility left in main source:**
- `prestamil-backend/src/main/java/com/ignis/prestamil/util/ContratoTestMain.java` is a standalone `main()` method with hardcoded fictitious client data used to test Jasper report PDF generation. It is compiled into the production artifact.

~~**JwtUtil.java and JwtAuthenticationFilter.java are empty (1 line each):**~~
RESOLVED 2026-05-15 (260515-04c) — ambos archivos eliminados del codebase. `filter/` está vacío; `util/JwtUtil.java` ya no existe.

~~**Loan/contract core domain is absent:**~~
RESOLVED 2026-05-22 (260522-h4a): Fase A completa — `Contrato`, `PartidaContrato`, `MovimientoContrato` implementados con entidades JPA, repositorios, DTOs, mapper y `ContratoService`/`ContratoController`. 5 endpoints REST operativos. Pendiente: refrendos, finiquitos, PDF (Fase B/C).

---

## Technical Debt

**Passwords stored with reversible encryption instead of BCrypt:**
- `BCryptPasswordEncoder` is declared as a `@Bean` in `SecurityConfig.java` but never injected into `UsuarioService`. The encrypt/decrypt flow uses `Encryptor` (custom AES). This split exists because the system was partially migrated from JWT to session-based auth without completing the password hashing migration.
- Files: `prestamil-backend/src/main/java/com/ignis/prestamil/config/SecurityConfig.java`, `prestamil-backend/src/main/java/com/ignis/prestamil/service/UsuarioService.java`

**Hardcoded role ID in frontend turno permission check:**
- `TurnosComponent` hardcodes `rolesPermitidosAbrir: number[] = [5]` (line 19) as the list of roles that can open a turn. The backend fetches this from a DB configuration (`Constantes.ROLES_PERMITIDOS_APERTURA_TURNOS`). The frontend does not read this setting; it uses a magic number.
- Files: `prestamil-frontend/src/app/prestamil/pages/turnos/turnos.component.ts`

**Menu URL mapping is a fragile hardcoded lookup table:**
- `AuthService.regenerateMenuUrls()` contains a manual string-to-URL mapping for menu items. Any menu item name change in the DB or backend breaks navigation silently.
- Files: `prestamil-frontend/src/app/prestamil/core/services/auth.service.ts` (lines 87-103)

**`UsuarioController` accepts the raw `Usuario` entity as `@RequestBody`:**
- `POST /api/usuarios` and `PUT /api/usuarios/{id}` accept the full `Usuario` JPA entity directly. This leaks internal field names, allows clients to attempt setting `creado`, `ultimoLogin`, etc., and tightly couples the API contract to the DB schema.
- Files: `prestamil-backend/src/main/java/com/ignis/prestamil/controller/UsuarioController.java`
- `ClienteController` and `CatalogoController` have the same pattern.

**`window.location.reload()` used to refresh menu state after turno actions:**
- After opening or closing a turn, the component forces a full page reload (`window.location.reload()`) as a workaround for menu state not updating reactively. This is a symptom of the menu state management architecture requiring improvement.
- Files: `prestamil-frontend/src/app/prestamil/pages/turnos/turnos.component.ts` (lines 67-69, 110-112)

**`setTimeout(() => {}, 100)` in `ngAfterViewInit` to avoid ViewChild timing issues:**
- `UsuariosComponent.ngAfterViewInit` wraps `loadInitialData()` in a 100ms setTimeout as a workaround for ViewChild availability timing.
- Files: `prestamil-frontend/src/app/prestamil/pages/usuarios/usuarios.component.ts` (lines 64-68)

**`MasterDataService` has estados hardcoded in Java:**
- The 32 Mexican states are hardcoded in a `List<String>` inside `MasterDataService`. This data would typically come from a reference table.
- Files: `prestamil-backend/src/main/java/com/ignis/prestamil/service/MasterDataService.java`

---

## Missing Error Handling

**No `@Valid` annotation on `UsuarioController`, `ClienteController`, `CatalogoController`, `ParametrosSistemaController` request bodies:**
- These controllers accept `@RequestBody` without `@Valid`. Invalid payloads (null required fields, wrong types) reach service/repository layers and produce generic 500 errors instead of 400 validation errors.
- Files: `prestamil-backend/src/main/java/com/ignis/prestamil/controller/UsuarioController.java` (lines 61, 67), `prestamil-backend/src/main/java/com/ignis/prestamil/controller/ClienteController.java` (lines 50, 56)

**`GlobalExceptionHandler` catches all `Exception` and returns the raw exception message:**
- `handleGlobalException` returns `ex.getMessage()` in the HTTP response body for all unhandled exceptions. This can leak internal class names, SQL errors, or stack information to clients.
- Files: `prestamil-backend/src/main/java/com/ignis/prestamil/exception/GlobalExceptionHandler.java` (lines 39-48)

**`Encryptor.encrypt()` returns `null` on failure silently:**
- If encryption fails, `encrypt()` returns `null` without logging or throwing. The caller stores `null` as the password in the DB.
- Files: `prestamil-backend/src/main/java/com/ignis/prestamil/util/Encryptor.java` (lines 38-42)

**~~`PlazoService.getParametrosPlazo()` returns `null` instead of throwing:~~** RESOLVED 2026-05-16 (quick task 260516-mns) — ahora lanza `ResourceNotFoundException` y acepta `sucursalId` como tercer parámetro. La firma es `getParametrosPlazo(Long idPlazo, Integer idTipoPrenda, Integer sucursalId)`.
- Files: `prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java`

**`TurnoService.obtenerTurnoActivo()` returns `null` instead of a typed Optional or empty response:**
- Callers must null-check the return value. `TurnoController.obtenerTurnoActivo()` wraps null in `ResponseEntity.ok(null)`, which sends a 200 with an empty body — semantically ambiguous.
- Files: `prestamil-backend/src/main/java/com/ignis/prestamil/service/TurnoService.java` (line 77), `prestamil-backend/src/main/java/com/ignis/prestamil/controller/TurnoController.java` (lines 47-50)

**Frontend `usuarios.component.ts` swallows load errors silently:**
- The `error` callback in `loadUsuarios()` only closes the loading modal and sets `loading = false`. No error message is shown to the user.
- Files: `prestamil-frontend/src/app/prestamil/pages/usuarios/usuarios.component.ts` (lines 119-128)

**`cambiarPassword` in `AuthService` does not include `withCredentials`:**
- The `cambiarPassword` call in `AuthService` (line 326) uses `this.http.post(url, body)` without explicit `withCredentials: true`. The `CredentialsInterceptor` should add it, but the login call sets it explicitly as documentation of intent while this one doesn't, creating inconsistency.
- Files: `prestamil-frontend/src/app/prestamil/core/services/auth.service.ts` (line 326)

---

## Performance Concerns

**`/api/usuarios` loads all users with no pagination by default:**
- `GET /api/usuarios` returns all users in a single list. `GET /api/usuarios/page` exists with `Pageable` support but the frontend `UsuariosComponent` calls `findAll()` (the unpaginated version).
- Files: `prestamil-backend/src/main/java/com/ignis/prestamil/controller/UsuarioController.java` (lines 39-45), `prestamil-frontend/src/app/prestamil/pages/usuarios/usuarios.component.ts` (line 96)

**`/api/clientes` has no pagination:**
- `ClienteController.findAll()` returns all clients. For a pawnshop with years of data this will grow unbounded.
- Files: `prestamil-backend/src/main/java/com/ignis/prestamil/controller/ClienteController.java` (lines 26-31)

**Caffeine cache only covers `sucursal` — no caching on catalogs or master data:**
- `CacheConfig` registers only the `"sucursal"` cache. Frequently read catalogs (tipos de prenda, catálogos, roles) are fetched from the DB on every request.
- Files: `prestamil-backend/src/main/java/com/ignis/prestamil/config/CacheConfig.java`

**`spring.jpa.show-sql=true` + `format_sql=true` adds logging overhead in all environments:**
- All SQL is formatted and written to the log. On high-frequency endpoints this has measurable I/O overhead.
- Files: `prestamil-backend/src/main/resources/application.properties`

---

## Dependency Concerns

**Spring Boot 3.2.5 — one minor version behind 3.4.x as of 2026:**
- `prestamil-backend/pom.xml` pins `spring-boot-starter-parent` to `3.2.5`. Spring Boot 3.2 reached end of OSS support in November 2024.

**JasperReports 6.21.0 — version from 2023, community fork (TIBCO) is the active branch:**
- `net.sf.jasperreports:jasperreports:6.21.0` is the last community release before TIBCO deprecated the open-source build. The library has known classpath conflicts with newer Jakarta EE. Updates are complex.
- Files: `prestamil-backend/pom.xml`

**`barbecue:1.5-beta1` is from 2007 and marked beta:**
- The barcode library `net.sourceforge.barbecue:barbecue:1.5-beta1` has no stable release and has not had commits in over a decade. It is a high-risk dependency for a production PDF generation pipeline.
- Files: `prestamil-backend/pom.xml`

**`escpos-coffee:4.1.0` (thermal printer) is imported but the feature is a stub:**
- The printer dependency is declared and compiled but the only implementation is `System.out.println`. Dead production dependency.
- Files: `prestamil-backend/pom.xml`, `prestamil-backend/src/main/java/com/ignis/prestamil/service/PrinterService.java`

---

## Development Progress

**Features considered functionally complete:**
- Authentication (session-based login/logout with single-session enforcement)
- SSE-based forced logout and turno-cerrado notifications
- User CRUD (`/api/usuarios` with search, create, update, soft-delete)
- Turno (shift) open/close management with permission check via DB config
- Sucursal configuration editing
- Empresa (company) catalog CRUD
- Catálogo/Prenda catalog read-only display
- Plazos y Periodos configuration CRUD
- Parámetros Generales display and editing
- Role listing (read-only)
- Password change endpoint (backend + frontend)

**Features in-progress or incomplete:**
- Avaluos/Contratos: Fase A backend completa (Contrato, PartidaContrato, MovimientoContrato). Frontend conectado a APIs reales (`avaluo.component.ts`, ruta `/avaluos`). Pendiente: PDF, refrendos, finiquitos (Fase B/C).
- Hardware/printer: UI shell only, backend stub
- Role-based access control enforcement (data model exists, enforcement absent)
- Contract lifecycle operations (refrendos, finiquitos) — not yet implemented
- Client-facing features (client search exists in backend; dedicated frontend page exists at `/clientes`)

---

## Branch Activity

**Backend (`prestamil-backend`):**
- `main` — current integration branch, 22 commits total
- `sesiones` — feature branch merged via PR #3; added stateful session + SSE logout
- `turnos` — feature branch merged via PR #2; added turno management
- `usuarios` — feature branch merged via PR #1; added user CRUD
- `remotes/origin/chava` — remote branch, last merged as PR #4 (`organizacion menu`, `adds plazos`)
- `remotes/origin/develop` — exists on remote but has no local tracking; purpose unknown

**Frontend (`prestamil-frontend`):**
- `main` — integration branch, 16 commits total
- `sesiones` — merged; added SSE and session handling
- `usuarios` — merged; added user management UI
- `remotes/origin/chava` — remote branch with UI contributions (menu organization, mock avaluos, catalogos)
- `remotes/origin/develop` — exists on remote, purpose unknown

**Overall:** The project is in active early development with a clear feature-branch merge workflow. Core infrastructure (auth, sessions, shift management) is done. The business-critical loan origination flow (contratos/avaluos) has not started on the backend.

---

*Concerns audit: 2026-05-22*
