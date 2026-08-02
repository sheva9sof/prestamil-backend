# System Architecture

**Analysis Date:** 2026-05-22

---

## Overview

Prestamil is a pawnshop management system (sistema de casa de empeño) built as a decoupled full-stack application. The backend is a Java Spring Boot REST API and the frontend is an Angular SPA. They communicate exclusively over HTTP using cookie-based session authentication. The system manages: users, roles, menu permissions, work shifts (turnos), clients, pledged items (prendas), branches (sucursales), companies, loan terms (plazos), and system configuration.

A distinctive business rule: the menu options visible to a user are gated by the existence of an active Turno (shift) and by the user's role. Closing a shift forcibly logs out all connected clients via SSE.

---

## Backend Architecture

**Framework:** Spring Boot 3.2.5 on Java 21. Pattern is layered MVC with a generic base abstraction.

**Request Lifecycle:**

1. HTTP request arrives at Spring MVC dispatcher.
2. `CorsConfig` (`config/CorsConfig.java`) validates the origin — only `http://localhost:4200` is allowed with credentials.
3. Spring Security `SecurityFilterChain` (`config/SecurityConfig.java`) resolves the `JSESSIONID` cookie against the JDBC session store via `HttpSessionSecurityContextRepository`.
4. If unauthenticated and not a public endpoint (`/auth/login`, `/auth/**`, `/auth/stream/**`), returns HTTP 401 (configured via `authenticationEntryPoint`).
5. Authenticated requests reach the `@RestController`.
6. Controller delegates to a `@Service`.
7. Service interacts with a `@Repository` (Spring Data JPA).
8. Repository queries the relational DB.
9. Entity is mapped to a response DTO via a MapStruct `@Mapper`.
10. Response DTO is serialized to JSON and returned.
11. `GlobalExceptionHandler` (`exception/GlobalExceptionHandler.java`, `@ControllerAdvice`) intercepts any thrown exception and returns a structured `ErrorResponse`.

**Layers:**

- **Controller** (`controller/`): `@RestController` beans. Thin — validates params, calls service, maps to `ResponseEntity`. Base path `/api/**` for protected resources; `/auth/**` for public endpoints.
- **Service** (`service/`): Business logic. Most extend `BaseService<T, ID, R>` which provides `findAll`, `findById`, `save`, `update`, `delete`, `deleteById`, `existsById`, `count`. Services are `@Transactional`.
- **Repository** (`repository/`): Spring Data JPA interfaces extending `BaseRepository<T, ID>` (which itself extends `JpaRepository`). No hand-written SQL; queries use derived method names or `Specification`.
- **Model** (`model/`): JPA `@Entity` classes with Lombok `@Data` / `@Getter` / `@Setter`. Table names are explicit via `@Table(name = ...)`.
- **Mapper** (`mapper/`): MapStruct `@Mapper(componentModel = "spring")` interfaces. Generated at compile time; Spring-managed beans.
- **Request/Response** (`request/`, `response/`): Plain Java DTOs. Requests carry inbound data; responses carry outbound data. Entities are NOT exposed directly (except a few legacy controller endpoints that accept raw entity bodies).
- **Config** (`config/`): `SecurityConfig`, `CorsConfig`, `SessionConfig` (`@EnableJdbcHttpSession`), `CacheConfig` (Caffeine, `sucursal` cache).
- **Exception** (`exception/`): `GlobalExceptionHandler` catches `ResourceNotFoundException` → 404, `BadRequestException` → 400, `Exception` (catch-all) → 500.
- **Util** (`util/`): `Encryptor` (AES/ECB symmetric password encryption), `Constantes` (system constants), `ContratoTestMain`, report row helpers (`PagoRow`, `PagoExtemporaneoRow`, `PrendaRow`). Note: `JwtUtil.java` and `JwtAuthenticationFilter.java` were deleted in task 260515-04c.
- **Filter** (`filter/`): Empty directory — `JwtAuthenticationFilter` was deleted in task 260515-04c.

**API Route Table:**

| Method | Path | Controller | Notes |
|--------|------|------------|-------|
| POST | `/auth/login` | `AuthController` | Public; creates session |
| POST | `/auth/logout` | `AuthController` | Public; invalidates session |
| GET | `/auth/stream/logout` | `LogoutStreamController` | Public; SSE stream per user |
| GET | `/api/usuarios` | `UsuarioController` | List all users |
| GET | `/api/usuarios/me` | `UsuarioController` | Current user profile + dynamic menu |
| GET | `/api/usuarios/page` | `UsuarioController` | Paginated |
| GET | `/api/usuarios/{id}` | `UsuarioController` | By ID |
| POST | `/api/usuarios` | `UsuarioController` | Create |
| PUT | `/api/usuarios/{id}` | `UsuarioController` | Update |
| DELETE | `/api/usuarios/{id}` | `UsuarioController` | Soft-delete (`estatus=false`) |
| GET | `/api/usuarios/buscar` | `UsuarioController` | Filter: nombre, nombreUsuario, estatus |
| PUT | `/api/usuarios/{id}/cambiar-password` | `UsuarioController` | Change password |
| POST | `/api/turnos/iniciar` | `TurnoController` | Open shift |
| POST | `/api/turnos/cerrar/{id}` | `TurnoController` | Close shift + SSE broadcast |
| GET | `/api/turnos/activo` | `TurnoController` | Get active shift |
| GET | `/api/sucursales` | `SucursalController` | Get unique branch (Caffeine-cached) |
| PUT | `/api/sucursales/{id}` | `SucursalController` | Update branch |
| GET | `/api/empresas` | `EmpresaController` | List companies |
| POST | `/api/empresas` | `EmpresaController` | Create |
| PUT | `/api/empresas/{id}` | `EmpresaController` | Update |
| GET | `/api/catalogos` | `CatalogoController` | List all catalog entries |
| GET | `/api/catalogos/tipo/{idTipo}` | `CatalogoController` | Filter by type |
| GET/POST/PUT/DELETE | `/api/catalogos/{id}` | `CatalogoController` | CRUD |
| GET | `/api/prendas/tipos` | `PrendaController` | Pawn item types |
| GET | `/api/prendas/subtipos/{idTipoPrenda}` | `PrendaController` | Sub-types by type |
| GET | `/api/prendas/valores/{idAtributo}` | `PrendaController` | Values by attribute |
| GET | `/api/plazos` | `PlazoController` | List loan terms |
| GET/POST/PUT | `/api/plazos/{id}` | `PlazoController` | CRUD |
| GET | `/api/plazos/{id}/parametros/{idTipo}` | `PlazoController` | Term params by prenda type (?sucursalId=1) — throws 404 if not found |
| GET | `/api/plazos/{id}/parametros` | `PlazoController` | List params por sucursal (?sucursalId=1) |
| PUT | `/api/plazos/{id}/parametros/{tipoPrendaId}` | `PlazoController` | Upsert param (?sucursalId=1) |
| GET | `/api/plazos/{id}/alhajas` | `PlazoController` | Tabla hechuras por sucursal (?sucursalId=1) |
| PUT | `/api/plazos/{id}/alhajas/{kilataje}/{hechura}` | `PlazoController` | Actualizar precio base de una fila |
| PUT | `/api/plazos/{id}/alhajas/precio-oro` | `PlazoController` | Recalcular todos usando precioBaseOro |
| GET | `/api/roles` | `RolController` | List roles |
| GET | `/api/roles/{id}` | `RolController` | Role by ID |
| GET/POST/PUT/DELETE | `/api/parametros-sistema/{id}` | `ParametrosSistemaController` | System params CRUD |
| GET | `/api/master-data/estados` | `MasterDataController` | Hardcoded list of Mexican states |
| POST | `/api/contratos` | `ContratoController` | Crear contrato con partidas; requiere turno activo |
| GET | `/api/contratos/{id}` | `ContratoController` | Detalle de contrato + partidas |
| GET | `/api/contratos/folio/{folio}` | `ContratoController` | Buscar por folio |
| GET | `/api/contratos/cliente/{clienteId}` | `ContratoController` | Historial contratos del cliente |
| GET | `/api/contratos/vencidos` | `ContratoController` | Contratos con estatus VENCIDO |

**Base Abstractions:**

- `BaseRepository<T, ID>` (`repository/BaseRepository.java`) — marker interface extending `JpaRepository`; annotated `@NoRepositoryBean`. All concrete repositories extend this.
- `BaseService<T, ID, R extends BaseRepository>` (`service/BaseService.java`) — provides CRUD template methods. Domain services extend it and override where business rules differ (e.g., `UsuarioService` overrides `save` to encrypt the password; overrides `deleteById` to soft-delete).

---

## Frontend Architecture

**Framework:** Angular 20 SPA with standalone components and lazy loading via `loadComponent`.

**Entry point:** `src/main.ts` → `bootstrapApplication(AppComponent, appConfig)`.

**Application config:** `src/app/app-config.ts` registers all root-level providers: `BrowserModule`, `AppRoutingModule`, `provideAnimations()`, `provideHttpClient(withInterceptorsFromDi())`, `CredentialsInterceptor`, `AuthErrorInterceptor`.

**Component Model:**

- Feature pages under `src/app/prestamil/pages/**` and `src/app/prestamil/dashboard/` are **standalone** components lazy-loaded on route activation.
- Layout shells (`AdminComponent`, `GuestComponent`) use the older NgModule pattern.
- `SharedModule` (`theme/shared/shared.module.ts`) re-exports: `CommonModule`, `FormsModule`, `ReactiveFormsModule`, `CardComponent`, `NgbModule` (ng-bootstrap), `NgScrollbarModule`. Feature components import `SharedModule` for UI primitives.
- UI framework: **ng-bootstrap** (modals, collapse, etc.).

**State Management:** No NgRx. State lives in `BehaviorSubject`s inside services:

| Subject | Service | Purpose |
|---------|---------|---------|
| `isAuthenticatedSubject` | `AuthService` | Boolean auth flag |
| `menuItemsSubject` | `AuthService` | Sidebar navigation items |
| `isLoggingOutSubject` | `AuthService` | Spinner control during logout |
| `currentTurnoSubject` | `TurnoService` | Active shift data |
| `connectionStatusSubject` | `AuthStreamService` | SSE connection state |

**New services added (2026-05-22):**
- `cliente.service.ts` — search and CRUD calls to `/api/clientes`
- `contrato.service.ts` — create and query contratos via `/api/contratos`
- `prenda.service.ts` — catalog calls to `/api/prendas/tipos`, `/api/prendas/subtipos/{id}`, `/api/prendas/valores/{id}`
- `session-warning.service.ts` — session expiry warning support

**New models added (2026-05-22):**
- `contrato.model.ts` — `ContratoRequest`, `ContratoResponse`, `PartidaContratoRequest`, `PartidaContratoResponse` interfaces
- `cliente.model.ts` — `Cliente` interface

`localStorage` persists user data (`authUser`) and menu structure (`menuItems`) across page refreshes. The actual session is carried by the browser's `JSESSIONID` cookie.

**Routing** (`src/app/app-routing.module.ts`):

```
/login           → GuestComponent  [loginGuard]
                    └── AuthSigninComponent (lazy)

/ (root)         → AdminComponent  [authGuard]
  /dashboard          → DashboardComponent (lazy)
  /sse-status         → SseStatusComponent (lazy)
  /usuarios           → UsuariosComponent (lazy)
  /clientes           → ClientesComponent (lazy)
  /turnos             → TurnosComponent (lazy)
  /hardware           → HardwareComponent (lazy)
  /catalogos/prendas       → PrendasComponent (lazy)
  /catalogos/descuentos    → DescuentosComponent (lazy)
  /configuracion/sucursal              → SucursalComponent (lazy)
  /configuracion/empresas              → EmpresasComponent (lazy)
  /configuracion/parametros-prestamo   → ParametrosPrestamoComponent (lazy)
  /configuracion/parametros-generales  → ParametrosGeneralesComponent (lazy)
  /configuracion/plazos-periodos       → PlazosPeriodosComponent (lazy)
  /configuracion/actualizar-password   → ActualizarPasswordComponent (lazy)
  /avaluos                             → AvaluoComponent (lazy)
  /**                  → redirect /dashboard
```

**HTTP Layer** — two class-based interceptors registered in `appConfig`:

1. **`CredentialsInterceptor`** (`core/interceptors/credentials.interceptor.ts`): Clones any request targeting `environment.apiUrl` and adds `withCredentials: true`. This causes the browser to send the `JSESSIONID` cookie on all cross-origin API calls.
2. **`AuthErrorInterceptor`** (`core/interceptors/auth-error.interceptor.ts`): Catches HTTP 401/403/440 on backend requests (excluding `/auth/login` and `/auth/logout`). Calls `AuthService.handleSessionInvalidation()` which clears local state and redirects to `/login`.

Base API URL: `http://localhost:8080` (from `src/environments/environment.ts`).

**Real-Time (SSE):**

`AuthStreamService` (`core/services/auth-stream.service.ts`) opens a native `EventSource` to `GET /auth/stream/logout?username=<user>` after login. Named events handled:

- `force-logout` — another login for the same user was detected; clears `localStorage` and forces `window.location.href = '/login?message=...'` after 2 s.
- `turno-cerrado` — the active shift was closed by a manager; same forced redirect.

Reconnection uses exponential backoff (base 2 s, max 5 attempts).

---

## Data Flow

**Login flow:**

1. `AuthSigninComponent` collects credentials → calls `AuthService.login(username, password)`.
2. `POST /auth/login` (no interceptor needed — sent with `withCredentials: true` explicitly).
3. Backend `AuthController.login()`:
   a. Sends `force-logout` SSE event to any existing open stream for that username.
   b. Delegates to `UsuarioService.login()`:
      - Looks up `Usuario` by `nombreUsuario`.
      - Decrypts stored password via `Encryptor.decrypt` and compares to plaintext request.
      - Updates `ultimoLogin`, `inicioSesion`, `ultimaActividad`.
      - Retrieves `Opcion` list via `Rol → RolOpcion → Opcion` chain.
      - Runs `filtrarOpcionesPorTurno()`: if no active shift, only turno-permitted roles see the Turnos menu; others receive 404.
      - Builds hierarchical `MenuResponse` list.
   c. Creates `UsernamePasswordAuthenticationToken` with `ROLE_USER`.
   d. Rotates session ID (`request.changeSessionId()`).
   e. Invalidates all previous sessions for the same user via `FindByIndexNameSessionRepository`.
   f. Saves `SecurityContext` to `HttpSession` explicitly (Spring Security 6 requirement).
   g. Returns `LoginResponse`.
4. Frontend `AuthService.setSession()`:
   - Stores user object in `localStorage` (`authUser` key).
   - Transforms `opciones` to `NavigationItem[]` via `menu-transformer.helper.ts`; stores in `localStorage` (`menuItems`).
   - Sets `isAuthenticatedSubject` to `true`.
   - Calls `AuthStreamService.connect(username)` to open SSE channel.
5. Angular router navigates to `/dashboard` (or `returnUrl` query param).

**Authenticated API call flow:**

1. Component calls a service method (e.g., `TurnoService.iniciar()`).
2. `HttpClient.post()` fires; `CredentialsInterceptor` clones request with `withCredentials: true`.
3. Browser attaches `JSESSIONID` cookie automatically.
4. Backend `SecurityFilterChain` resolves the session; `SecurityContext` is loaded; request is authorized.
5. Controller → Service → Repository → DB → Entity → Mapper → DTO → JSON response.
6. On 401/403/440, `AuthErrorInterceptor` triggers `handleSessionInvalidation()`.

**Turno lifecycle flow:**

1. Manager opens `TurnosComponent`; `TurnoService.refreshActivo()` calls `GET /api/turnos/activo`.
2. **Iniciar turno**: `POST /api/turnos/iniciar` → `TurnoService.iniciarTurno()` verifies no active shift exists, creates `Turno` with `activo=true`. Frontend calls `AuthService.refreshMenuFromBackend()` (`GET /api/usuarios/me`) to get the full menu, then reloads the page.
3. **Cerrar turno**: `POST /api/turnos/cerrar/{id}` → `TurnoService.cerrarTurno()` sets `activo=false`. `TurnoController.cerrarTurno()` then calls `LogoutStreamController.sendTurnoCerradoEvent()` for every connected user, broadcasting `turno-cerrado`. All clients redirect to `/login`.

---

## Authentication & Authorization

**Mechanism:** Stateful HTTP session stored in a relational DB via Spring Session JDBC (`@EnableJdbcHttpSession` in `config/SessionConfig.java`). Session ID transported as an HttpOnly `JSESSIONID` cookie. No JWT tokens are used in production (`JwtUtil.java` and `JwtAuthenticationFilter.java` were deleted in task 260515-04c).

**Session management:**

- `SessionConfig.java` creates a `SpringSessionBackedSessionRegistry`.
- `SecurityConfig.java` configures `maximumSessions(1)`, `maxSessionsPreventsLogin(false)` — new login wins, old session is evicted.
- On login, `AuthController.invalidatePreviousSessions()` deletes old sessions from the store via `FindByIndexNameSessionRepository.findByPrincipalName()`.
- `SecurityContextRepository` is `HttpSessionSecurityContextRepository` with `requireExplicitSave(true)` — `AuthController` must call `securityContextRepository.saveContext()` explicitly after login.

**Password storage:**

Passwords are stored as AES-128-ECB ciphertext using `util/Encryptor.java`. The AES key is derived from SHA-256 of a hardcoded secret string. Passwords are decryptable. The `BCryptPasswordEncoder` bean declared in `SecurityConfig` is NOT used for login comparison.

**Menu-level authorization:**

Role → RolOpcion → Opcion (menu items). At login and at `GET /api/usuarios/me`, `UsuarioService.getOpcionesByUsuario()` fetches the user's allowed options, then `filtrarOpcionesPorTurno()` applies shift-state gating. Role IDs permitted to open shifts are read from the `ParametrosSistema` table under the constant key `ROLES_PERMITIDOS_APERTURA_TURNOS`.

**Frontend guards:**

- `authGuard` (`core/guards/auth.guard.ts`): Checks `AuthService.isAuthenticated()` — this only checks `localStorage` presence; it does NOT verify with the backend. A stale `localStorage` entry will pass the guard until the first API call returns 401.
- `loginGuard` (`core/guards/login.guard.ts`): Blocks `/login` if `isAuthenticated()` is true; redirects to `/dashboard`.

---

## Module Breakdown

| Domain | Backend Files | Frontend Files |
|--------|--------------|----------------|
| Auth / Session | `AuthController`, `UsuarioService` (login method), `SecurityConfig`, `SessionConfig`, `LogoutStreamController` | `AuthService`, `AuthStreamService`, `CredentialsInterceptor`, `AuthErrorInterceptor`, `authGuard`, `loginGuard`, `login/login.component.ts` |
| Users | `UsuarioController`, `UsuarioService`, `UsuarioRepository`, `Usuario`, `UsuarioMapper` | `UsuariosComponent`, `usuario.service.ts` |
| Turnos (Shifts) | `TurnoController`, `TurnoService`, `TurnoRepository`, `Turno`, `TurnoMapper` | `TurnosComponent`, `turno.service.ts` |
| Clients | `ClienteController`, `ClienteService`, `ClienteRepository`, `Cliente`, `ClienteMapper` | `ClientesComponent` |
| Prendas | `PrendaController`, `TipoPrendaService`, `CatSubtipoPrendaService`, `CatValorPrendaService`, `PrendaMapper` | `PrendasComponent` |
| Catalogos | `CatalogoController`, `CatalogoService`, `CatalogoRepository`, `Catalogo`, `CatalogoMapper` | `DescuentosComponent` |
| Plazos | `PlazoController`, `PlazoService`, `PlazoRepository`, `PlazoParametroRepository`, `PlazoHechuraAlhajaRepository`, `PlazoMapper`, `PlazoParametroMapper`, `PlazoHechuraAlhajaMapper` | `PlazosPeriodosComponent`, `ParametrosPrestamoComponent`, `plazo.service.ts` |
| Sucursal | `SucursalController`, `SucursalService`, `SucursalRepository`, `Sucursal`, `SucursalMapper` | `SucursalComponent` |
| Empresas | `EmpresaController`, `EmpresaService`, `EmpresaRepository`, `Empresa`, `EmpresaMapper` | `EmpresasComponent` |
| Contratos / Avaluos | `ContratoController`, `ContratoService`, `ContratoRepository`, `PartidaContratoRepository`, `Contrato`, `PartidaContrato`, `MovimientoContrato`, `ContratoMapper` | `avaluo.component.ts`, `contrato.service.ts`, `contrato.model.ts` |
| Roles | `RolController`, `RolService`, `RolRepository`, `Rol`, `RolMapper` | `rol.service.ts` |
| Parametros Sistema | `ParametrosSistemaController`, `ParametrosSistemaService`, `ParametrosSistemaRepository` | `ParametrosGeneralesComponent` |
| Master Data | `MasterDataController`, `MasterDataService` | used in client/sucursal forms |
| Config (infra) | `CorsConfig`, `SecurityConfig`, `SessionConfig`, `CacheConfig` | `appConfig`, `environment.ts` |
| Error handling | `GlobalExceptionHandler`, `ResourceNotFoundException`, `BadRequestException`, `ValidationException` | `AuthErrorInterceptor` |
| Crypto | `Encryptor` | — |
| Reports | `PrinterService`, `PagoRow`, `PrendaRow`, Jasper `.jrxml` files | `HardwareComponent` |

---

*Architecture analysis: 2026-05-22*
