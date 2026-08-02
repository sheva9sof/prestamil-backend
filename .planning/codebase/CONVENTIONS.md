# Code Conventions

**Analysis Date:** 2026-05-12

---

## Naming Conventions

### Backend (Java / Spring Boot)

**Classes — strict suffix pattern:**
- Controllers: `*Controller` — `ClienteController`, `TurnoController`, `MasterDataController`
- Services: `*Service` — `ClienteService`, `TurnoService`, `BaseService`
- Repositories: `*Repository` — `ClienteRepository`, `BaseRepository`
- Mappers: `*Mapper` — `ClienteMapper`, `TurnoMapper`
- Request DTOs: `*Request` — `LoginRequest`, `CambiarPasswordRequest`, `EmpresaRequest`
- Response DTOs: `*Response` — `ClienteResponse`, `ErrorResponse`, `TurnoResponse`
- JPA Entities: no suffix, domain noun — `Cliente`, `Turno`, `Plazo`, `Sucursal`, `Empresa`
- Configs: `*Config` — `SecurityConfig`, `CorsConfig`, `CacheConfig`
- Exceptions: descriptive name — `ResourceNotFoundException`, `BadRequestException`, `ValidationException`
- Catalog/enum-like entities prefix with `Cat`: `CatSubtipoPrenda`, `CatValorPrenda`
- Composite keys use `*Id` suffix: `RolOpcionId`, `PlazoParametroId`

**Methods & Fields:**
- camelCase throughout; Spanish domain names preserved: `nombre`, `apellidoPaterno`, `nombreUsuario`, `razonSocial`
- Boolean flags use standard Lombok accessors: `activo` → `getActivo()` / `setActivo()`
- Constants in `Constantes.java`: SCREAMING_SNAKE_CASE — `AVISO_CONTRATO`, `ROLES_PERMITIDOS_APERTURA_TURNOS`

**Packages (under `com.ignis.prestamil`):**
- `config`, `controller`, `exception`, `filter`, `mapper`, `model`, `repository`, `request`, `response`, `service`, `util`

**Database columns:** snake_case (`apellido_paterno`, `no_exterior`, `fecha_inicio`). Always declared with explicit `@Column(name = "...")`.

### Frontend (Angular / TypeScript)

**Files — kebab-case with type suffix:**
- `*.component.ts` / `*.component.html` / `*.component.scss`
- `*.service.ts` — `auth.service.ts`, `turno.service.ts`, `usuario.service.ts`
- `*.guard.ts` — `auth.guard.ts`, `login.guard.ts`
- `*.interceptor.ts` — `credentials.interceptor.ts`, `auth-error.interceptor.ts`
- `*.model.ts` — `usuario.model.ts`, `turno.model.ts`
- `*.helper.ts` — `menu-transformer.helper.ts`

**Classes & Interfaces:**
- PascalCase; no `I` prefix on interfaces: `Usuario`, `LoginResponse`, `PageResponse<T>`
- Component classes: `EmpresasComponent`, `TurnosComponent`, `AuthSigninComponent`
- Service classes: `AuthService`, `TurnoService`, `UsuarioService`
- Interceptor classes: `CredentialsInterceptor`, `AuthErrorInterceptor`
- Guards: functional style (not class-based): `authGuard`, `loginGuard`

**Properties & Methods:**
- camelCase: `isAuthenticated$`, `currentTurno$`, `loadInitialData()`, `aplicarFiltros()`
- Private BehaviorSubjects end with `Subject`: `isAuthenticatedSubject`, `currentTurnoSubject`
- Public observables end with `$`: `isAuthenticated$`, `menuItems$`, `currentTurno$`
- Private readonly constants: SCREAMING_SNAKE_CASE — `AUTH_USER_KEY`, `MENU_ITEMS_KEY`, `API_URL`
- Boolean flags: `isLoading`, `isCreating`, `isLoadingData`, `puedeAbrir`
- Message strings: `successMessage`, `errorMessage`

---

## Backend Patterns

### Layering Rules

Controllers: receive request → call service → map entity to response → return HTTP response. No business logic.
Services: own business logic, transaction boundaries, throw domain exceptions.
Repositories: Spring Data JPA queries only.
Mappers: convert between entity and request/response DTOs only.

```
Request → Controller → Service → Repository
                ↓           ↑
              Mapper ←→ Mapper
```

### Dependency Injection Style

Two styles coexist. Prefer `@RequiredArgsConstructor` for new code:

**With `@RequiredArgsConstructor` (preferred):**
```java
@Service
@RequiredArgsConstructor
public class TurnoService {
    private final TurnoRepository turnoRepository;
    private final UsuarioRepository usuarioRepository;
}
```

**Explicit constructor (older pattern, still common):**
```java
public EmpresaController(EmpresaService empresaService, EmpresaMapper empresaMapper) {
    this.empresaService = empresaService;
    this.empresaMapper = empresaMapper;
}
```

### Lombok Usage

- Entities: `@Getter @Setter` only — never `@Data` (avoids JPA `equals`/`hashCode` pitfalls)
- Response DTOs: `@Getter @Setter` or `@Data @AllArgsConstructor` for compact types like `ErrorResponse`
- Request DTOs: `@Getter @Setter`
- Services/Controllers: `@RequiredArgsConstructor` when using field injection; `@Slf4j` when logging

### Base Classes

**`BaseService<T, ID, R extends BaseRepository<T, ID>>`**
- File: `src/main/java/com/ignis/prestamil/service/BaseService.java`
- Provides: `findAll()`, `findAll(Pageable)`, `findById(id)` (throws `ResourceNotFoundException`), `save()`, `update()`, `delete()`, `deleteById()`, `existsById()`, `count()`
- All domain services extend this. Override `save()` or `update()` for domain logic (e.g., `ClienteService.save()`)

**`BaseRepository<T, ID>`**
- File: `src/main/java/com/ignis/prestamil/repository/BaseRepository.java`
- Extends `JpaRepository`, annotated `@NoRepositoryBean`

### DTO Pattern

Three separate DTO types per entity:
1. `<Entity>Request` in `request/` — inbound payload, `@Getter @Setter`
2. `<Entity>Response` in `response/` — outbound payload, `@Getter @Setter`
3. `<Entity>Mapper` in `mapper/` — `@Component`, manual mapping via `to<Entity>Response()` and `to<Entity>()` methods

MapStruct is declared in `pom.xml` but all current mappers are hand-written (no `@Mapper` annotations). Do not use MapStruct; follow the hand-written pattern.

### Transaction Pattern

```java
@Service
@Transactional          // class-level: all public methods are transactional
public class EmpresaService extends BaseService<...> { }

@Transactional(readOnly = true)  // method-level override for reads
public TurnoResponse obtenerTurnoActivo() { ... }
```

### Error Handling

Custom exception types — all extend `RuntimeException`:
- `ResourceNotFoundException` (HTTP 404) — `exception/ResourceNotFoundException.java`
- `BadRequestException` (HTTP 400) — `exception/BadRequestException.java`
- `ValidationException` (HTTP 400) — `exception/ValidationException.java`

Centralized handler at `exception/GlobalExceptionHandler.java` (`@ControllerAdvice`) returns `ErrorResponse`:
```json
{ "timestamp": "...", "status": 404, "error": "Not Found", "message": "...", "path": "..." }
```

Standard throw patterns:
```java
// Not-found (in BaseService):
.orElseThrow(() -> new ResourceNotFoundException("Recurso no encontrado con id: " + id));

// Business rule violation (in service layer):
throw new ValidationException("Ya existe un turno activo en el sistema con ID: " + t.getId());
```

Error messages are always in Spanish.

### Logging

`@Slf4j` on controllers that log entry points:
```java
@Slf4j
public class TurnoController {
    log.info("Petición recibida: Iniciar Turno");
    log.info("Petición recibida: Cerrar Turno ID {}", turnoId);
}
```
Not applied uniformly — only `TurnoController` uses it currently. Services do not log.

### Caching

Caffeine in-memory cache configured in `config/CacheConfig.java`. Cache name `"sucursal"`, 30-minute expiry, max 100 entries. Applied via `@Cacheable("sucursal")` in `SucursalService`.

### Security

Session-based stateful auth (Spring Session JDBC, `JSESSIONID` cookie):
- Public: `POST /auth/login`
- Protected: all other endpoints require authenticated session
- `SecurityConfig.java` disables CSRF, sets `SameSite=Lax`, manages session concurrency (max 1 session per user)
- `JwtAuthenticationFilter.java` exists but is not active — ignore it

---

## Frontend Patterns

### Component Style

All page-level and feature components are standalone (no NgModule declarations):
```typescript
@Component({
  selector: 'app-empresas',
  standalone: true,
  imports: [CommonModule, SharedModule, FormsModule],
  templateUrl: './empresas.component.html',
  styleUrls: ['./empresas.component.scss']
})
export class EmpresasComponent implements OnInit { }
```

### Lifecycle Hooks

- `ngOnInit`: load data, subscribe to observables
- `ngAfterViewInit`: access `@ViewChild` refs before use. Use `setTimeout(() => { ... }, 100)` to defer initial data load when a loading modal is used (see `UsuariosComponent`)

### Service Injection Style

Two styles coexist. Prefer `inject()` for services, constructor for components:

**`inject()` in services (preferred for new services):**
```typescript
@Injectable({ providedIn: 'root' })
export class TurnoService {
  private http = inject(HttpClient);
  private authService = inject(AuthService);
}
```

**Constructor injection in components:**
```typescript
constructor(
  private turnoService: TurnoService,
  private authService: AuthService,
  private modalService: NgbModal
) {}
```

### HTTP Pattern

Services call `HttpClient` with typed generics. API base URL always from `environment.apiUrl`:
```typescript
private readonly API_URL = environment.apiUrl + '/api/usuarios';
return this.http.get<UsuarioResponse[]>(this.API_URL);
```

`withCredentials: true` is set automatically by `CredentialsInterceptor` for all backend requests. Do not set it manually in new service methods.

Avoid making HTTP calls directly from page components (some legacy components like `EmpresasComponent` do this). Prefer injecting a service.

### Reactive State

BehaviorSubject pattern for shared state in services:
```typescript
private currentTurnoSubject = new BehaviorSubject<Turno | null>(null);
public currentTurno$ = this.currentTurnoSubject.asObservable();

// Update state on API call:
return this.http.post<Turno>(...).pipe(tap(t => this.currentTurnoSubject.next(t)));

// Consume in component:
this.turnoService.currentTurno$.subscribe(t => this.turno = t);
```

No NgRx or other state library. All shared state lives in services.

### Observable Error Handling

Use object form for subscribe callbacks:
```typescript
this.service.doSomething().subscribe({
  next: (data) => { this.data = data; },
  error: (err) => { this.errorMessage = '...'; console.error(err); }
});
```

Use `catchError` + `of(fallback)` for non-critical reads that should not block page load:
```typescript
this.http.get<any>(url).pipe(catchError(() => of({ error: true, data: [] })))
```

### Modal Pattern

Confirmation and loading dialogs use `@ng-bootstrap/ng-bootstrap NgbModal` with `@ViewChild` template refs:
```typescript
@ViewChild('confirmModal') confirmModalTemplate!: TemplateRef<any>;
const modalRef = this.modalService.open(this.confirmModalTemplate, { centered: true });
modalRef.result.then((result) => { if (result === 'confirm') { ... } }, () => {});
```

### Error & Success Messages

Components expose `errorMessage` and `successMessage` string properties bound to the template. Auto-dismiss success with `setTimeout`:
```typescript
this.successMessage = 'Empresa creada exitosamente';
setTimeout(() => { this.successMessage = ''; }, 5000);
```

Map HTTP error status codes to user-friendly Spanish strings:
```typescript
if (error.status === 401 || error.status === 403) {
  this.errorMessage = 'No tienes permisos para esta acción.';
} else if (error.status === 400) {
  this.errorMessage = error.error?.message || 'Verifica los datos ingresados.';
} else if (error.status === 0) {
  this.errorMessage = 'No se pudo conectar con el servidor.';
}
```

### Routing Pattern

Lazy-loaded standalone components via `loadComponent`:
```typescript
{
  path: 'turnos',
  loadComponent: () => import('./prestamil/pages/turnos/turnos.component')
    .then((c) => c.TurnosComponent)
}
```

Route groups: `configuracion/*`, `catalogos/*`. Protected by `authGuard`. Wildcard `**` redirects to `dashboard`.

### Import Grouping

Comments separate import sections in component files:
```typescript
// angular import
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';

// project import
import { SharedModule } from 'src/app/theme/shared/shared.module';
import { AuthService } from '../../../core/services/auth.service';
```

---

## Code Style

### Frontend Linting

ESLint + `@angular-eslint` — config: `prestamil-frontend/eslint.config.mjs`

Enforced rules:
- Component selectors: element type, `app-` prefix, kebab-case: `app-turnos`, `app-usuarios`
- Directive selectors: attribute type, `app` prefix, camelCase
- `@angular-eslint/component-class-suffix`: **off** — classes without `Component` suffix are allowed
- Extends: `eslint:recommended`, `@typescript-eslint/recommended`, `@angular-eslint/recommended`

Run: `npm run lint` / `npm run lint:fix`

### Frontend Formatting

Prettier (v3.6.1) — run: `npm run prettier` (writes to `./src`). No `.prettierrc` file — uses Prettier defaults.

### Backend Code Style

No Checkstyle, SpotBugs, or google-java-format configured. Conventions enforced by agreement:
- 4-space indentation
- All `@Column` annotations explicit with `name` attribute
- Javadoc on public service methods in Spanish, including `@param` and `@return`
- Inline step comments in service methods explaining intent

---

## API Design Conventions

### URL Naming

Base prefix `/api/` for all REST resources except auth:
```
POST  /auth/login
POST  /api/auth/logout    (exception: uses /api prefix)

GET   /api/empresas
GET   /api/clientes
GET   /api/usuarios
GET   /api/roles
GET   /api/turnos
GET   /api/catalogos
GET   /api/master-data/estados
GET   /api/parametros-sistema
```

Kebab-case for multi-word resource paths: `/api/master-data`, `/api/parametros-sistema`.

Actions (non-CRUD): `/api/turnos/iniciar`, `/api/turnos/cerrar/{id}`, `/api/usuarios/{id}/cambiar-password`.

Search endpoints: `/api/clientes/search?q=`, `/api/usuarios/buscar?nombre=&estatus=`.

Self-profile: `GET /api/usuarios/me`.

### HTTP Methods

| Operation      | Method | Status   |
|----------------|--------|----------|
| List all       | GET    | 200      |
| Get by ID      | GET    | 200 / 404|
| Paginated list | GET    | 200      |
| Create         | POST   | 201      |
| Update         | PUT    | 200      |
| Delete         | DELETE | 204      |
| Named action   | POST   | 200 / 201|

### Response Structure

Success (entity): DTO directly, no wrapper:
```json
{ "id": 1, "nombre": "Acme", "razonSocial": "Acme SA" }
```

Success (list): JSON array directly:
```json
[{ "id": 1, ... }, { "id": 2, ... }]
```

Paginated (Spring Page): `{ "content": [...], "totalElements": N, "totalPages": N, "size": N, "number": N }`

Error (from `GlobalExceptionHandler`):
```json
{
  "timestamp": "2026-05-12T10:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Recurso no encontrado con id: 5",
  "path": "/api/clientes/5"
}
```

### Consistency Notes

- Some controllers accept raw `@RequestBody Cliente` (entity) instead of a `ClienteRequest` DTO — for new endpoints, always define a `*Request` DTO.
- Some methods return `T` directly with `@ResponseStatus`, others return `ResponseEntity<T>` — prefer `ResponseEntity<T>` for explicit status control in new controllers.
- Ad-hoc responses (e.g., `cambiarPassword` returning `Map<String, String>`) should be replaced with typed `*Response` DTOs in new endpoints.

---

## Comments & Documentation

### Backend

Javadoc on public service methods in Spanish:
```java
/**
 * Busca clientes por nombre completo o teléfono
 * @param searchTerm Término de búsqueda
 * @return Lista de clientes que coinciden con la búsqueda
 */
```

Inline step comments in service methods explain business logic:
```java
// 1. Obtener el usuario autenticado
// 2. Validar que no exista NINGÚN turno activo
// 3. Crear, configurar y guardar el nuevo turno
```

### Frontend

JSDoc on public `AuthService` methods with `@param` / `@returns` in Spanish. Inline comments explain security decisions (cookie handling, Spring Security 6 requirements).

`console.log` / `console.debug` is heavily used throughout services and components for debugging. These are not gated by environment in most places and remain in production builds. New code should use `if (!environment.production) { console.debug(...); }`.
