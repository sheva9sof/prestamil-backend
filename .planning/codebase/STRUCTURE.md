# Project Structure

**Analysis Date:** 2026-05-22

---

## Repository Layout

```
prestamil/                           # Monorepo root (no build tooling at root level)
├── .planning/                       # GSD planning documents (not committed to subproject repos)
│   └── codebase/                   # Generated analysis documents
├── .idea/                           # IntelliJ IDEA workspace config
├── prestamil-backend/               # Java Spring Boot REST API (own git repo)
└── prestamil-frontend/              # Angular SPA (own git repo)
```

Each subproject has its own `.git` directory and is branched independently (current branch: `Emmanuel`).

---

## Backend Structure

Root: `prestamil-backend/`

```
prestamil-backend/
├── pom.xml                              # Maven build: Java 21, Spring Boot 3.2.5, MapStruct, Lombok
├── mvnw / mvnw.cmd                      # Maven wrapper
├── output/                              # Non-Maven build artifacts directory
├── target/                              # Maven build output (generated; not committed)
└── src/
    ├── main/
    │   ├── java/com/ignis/prestamil/
    │   │   ├── PrestamilApplication.java          # @SpringBootApplication entry point
    │   │   │
    │   │   ├── config/                            # Spring configuration beans
    │   │   │   ├── CacheConfig.java               # Caffeine in-memory cache; "sucursal" cache 30-min TTL, max 100 entries
    │   │   │   ├── CorsConfig.java                # Allows http://localhost:4200 with credentials
    │   │   │   ├── SecurityConfig.java            # Filter chain: session policy, 1 session/user, CSRF off, logout
    │   │   │   └── SessionConfig.java             # @EnableJdbcHttpSession; SpringSessionBackedSessionRegistry
    │   │   │
    │   │   ├── controller/                        # @RestController beans — thin, delegate to services
    │   │   │   ├── AuthController.java            # POST /auth/login, POST /auth/logout
    │   │   │   ├── CatalogoController.java        # CRUD /api/catalogos, GET /api/catalogos/tipo/{id}
    │   │   │   ├── ClienteController.java         # CRUD /api/clientes, search endpoint
    │   │   │   ├── ContratoController.java        # POST /api/contratos, GET /api/contratos/{id|folio|cliente|vencidos}
    │   │   │   ├── EmpresaController.java         # GET/POST/PUT /api/empresas
    │   │   │   ├── LogoutStreamController.java    # GET /auth/stream/logout — SSE emitter per user
    │   │   │   ├── MasterDataController.java      # GET /api/master-data/estados
    │   │   │   ├── ParametrosSistemaController.java # CRUD /api/parametros-sistema
    │   │   │   ├── PlazoController.java           # CRUD /api/plazos + /parametros + /alhajas endpoints
    │   │   │   ├── PrendaController.java          # GET /api/prendas/tipos|subtipos/{id}|valores/{id}
    │   │   │   ├── RolController.java             # GET /api/roles
    │   │   │   ├── SucursalController.java        # GET /api/sucursales, PUT /api/sucursales/{id}
    │   │   │   ├── TurnoController.java           # POST /iniciar, /cerrar/{id}, GET /activo
    │   │   │   └── UsuarioController.java         # CRUD /api/usuarios, GET /me, /buscar, PUT /{id}/cambiar-password
    │   │   │
    │   │   ├── exception/                         # Custom exceptions + global handler
    │   │   │   ├── BadRequestException.java       # RuntimeException → HTTP 400
    │   │   │   ├── GlobalExceptionHandler.java    # @ControllerAdvice; maps to ErrorResponse
    │   │   │   ├── ResourceNotFoundException.java # RuntimeException → HTTP 404
    │   │   │   └── ValidationException.java       # Business rule violation → HTTP 400
    │   │   │
    │   │   ├── filter/                            # (empty — JwtAuthenticationFilter deleted in 260515-04c)
    │   │   │
    │   │   ├── mapper/                            # MapStruct @Mapper interfaces (Spring-managed)
    │   │   │   ├── CatalogoMapper.java
    │   │   │   ├── ClienteMapper.java
    │   │   │   ├── ContratoMapper.java            # Maps Contrato/PartidaContrato ↔ request/response DTOs
    │   │   │   ├── EmpresaMapper.java
    │   │   │   ├── ParametrosSistemaMapper.java
    │   │   │   ├── PlazoMapper.java
    │   │   │   ├── PlazoHechuraAlhajaMapper.java
    │   │   │   ├── PlazoParametroMapper.java
    │   │   │   ├── PrendaMapper.java              # Maps TipoPrenda, CatSubtipoPrenda, CatValorPrenda
    │   │   │   ├── RolMapper.java
    │   │   │   ├── SucursalMapper.java
    │   │   │   ├── TipoPrendaMapper.java
    │   │   │   ├── TurnoMapper.java
    │   │   │   └── UsuarioMapper.java             # Also maps Usuario + List<Opcion> → LoginResponse
    │   │   │
    │   │   ├── model/                             # JPA @Entity classes
    │   │   │   ├── CatSubtipoPrenda.java          # Pledge item sub-type (level 2 of 3)
    │   │   │   ├── Contrato.java                  # Loan contract header (folio, cliente, turno, plazo, montos, estatus)
    │   │   │   ├── EstatusContrato.java           # Enum: VIGENTE, VENCIDO, DESEMPEÑADO, EN_VENTA
    │   │   │   ├── MovimientoContrato.java        # Contract movement history (refrendo, finiquito, abono)
    │   │   │   ├── PartidaContrato.java           # Pledge line item within a contract
    │   │   │   ├── CatValorPrenda.java            # Pledge item attribute value (level 3 of 3)
    │   │   │   ├── Catalogo.java                  # Generic catalog lookup table
    │   │   │   ├── Cliente.java                   # Customer record
    │   │   │   ├── Configuracion.java             # Config key-value (controls turno-gating rules)
    │   │   │   ├── Direccion.java                 # Address (associated with Cliente)
    │   │   │   ├── Empresa.java                   # Company/business entity
    │   │   │   ├── Opcion.java                    # Menu option (id, opcion, idPadre, icono, nombreIcono)
    │   │   │   ├── ParametrosSistema.java         # System parameter key-value store
    │   │   │   ├── Plazo.java                     # Loan term definition
    │   │   │   ├── PlazoHechuraAlhaja.java        # Tabla de hechuras de oro por plazo, sucursal, kilataje y hechura
    │   │   │   ├── PlazoHechuraAlhajaId.java      # Clave compuesta (idPlazo, sucursalId, kilataje, hechura)
    │   │   │   ├── PlazoParametro.java            # Loan term x prenda-type parameters (multi-sucursal)
    │   │   │   ├── PlazoParametroId.java          # Composite key for PlazoParametro (plazoId, tipoPrendaId, sucursalId)
    │   │   │   ├── Rol.java                       # Role; has List<RolOpcion>; getOpciones() helper
    │   │   │   ├── RolOpcion.java                 # Many-to-many join: Rol ↔ Opcion
    │   │   │   ├── RolOpcionId.java               # Composite key for RolOpcion
    │   │   │   ├── Sucursal.java                  # Branch (single-record by design)
    │   │   │   ├── TipoPrenda.java                # Pledge item type (top-level, level 1 of 3)
    │   │   │   ├── Turno.java                     # Shift: id_turno, id_usuario, fecha_inicio, fecha_fin, activo
    │   │   │   ├── TurnoEstatus.java              # Shift status enum/entity
    │   │   │   └── Usuario.java                   # System user: nombreUsuario, password (AES), rol, session fields
    │   │   │
    │   │   ├── repository/                        # Spring Data JPA interfaces
    │   │   │   ├── BaseRepository.java            # @NoRepositoryBean; extends JpaRepository<T,ID>
    │   │   │   ├── CatSubtipoPrendaRepository.java
    │   │   │   ├── ContratoRepository.java        # findByFolio(), findByClienteId(), findByEstatus()
    │   │   │   ├── PartidaContratoRepository.java # findByContratoId()
    │   │   │   ├── CatValorPrendaRepository.java
    │   │   │   ├── CatalogoRepository.java
    │   │   │   ├── ClienteRepository.java
    │   │   │   ├── ConfiguracionRepository.java
    │   │   │   ├── DireccionRepository.java
    │   │   │   ├── EmpresaRepository.java
    │   │   │   ├── OpcionRepository.java
    │   │   │   ├── ParametrosSistemaRepository.java
    │   │   │   ├── PlazoHechuraAlhajaRepository.java
    │   │   │   ├── PlazoParametroRepository.java
    │   │   │   ├── PlazoRepository.java
    │   │   │   ├── RolOpcionRepository.java
    │   │   │   ├── RolRepository.java
    │   │   │   ├── SucursalRepository.java
    │   │   │   ├── TipoPrendaRepository.java
    │   │   │   ├── TurnoRepository.java           # findByActivo(boolean) — the key query for shift gating
    │   │   │   └── UsuarioRepository.java         # findByNombreUsuario(), findWithRolAndOpcionesByNombreUsuario()
    │   │   │
    │   │   ├── request/                           # Inbound DTOs
    │   │   │   ├── CambiarPasswordRequest.java    # {passwordActual, passwordNueva}
    │   │   │   ├── ContratoRequest.java           # Inbound: cliente, turno, plazo, partidas, fechas, montos
    │   │   │   ├── PartidaContratoRequest.java    # Inbound: prenda line item fields
    │   │   │   ├── EmpresaRequest.java
    │   │   │   ├── LoginRequest.java              # {username, password}
    │   │   │   ├── ParametrosSistemaRequest.java
    │   │   │   ├── PlazoHechuraAlhajaRequest.java
    │   │   │   ├── PlazoParametroRequest.java
    │   │   │   ├── PlazoRequest.java
    │   │   │   └── SucursalRequest.java
    │   │   │
    │   │   ├── response/                          # Outbound DTOs
    │   │   │   ├── CatSubtipoPrendaResponse.java
    │   │   │   ├── ContratoResponse.java          # Outbound: full contract + partidas list
    │   │   │   ├── PartidaContratoResponse.java   # Outbound: single pledge line item
    │   │   │   ├── CatValorPrendaResponse.java
    │   │   │   ├── CatalogoResponse.java
    │   │   │   ├── ClienteResponse.java
    │   │   │   ├── DireccionResponse.java
    │   │   │   ├── EmpresaResponse.java
    │   │   │   ├── ErrorResponse.java             # {timestamp, status, error, message, path}
    │   │   │   ├── LoginResponse.java             # Login result; includes opciones[] for sidebar menu
    │   │   │   ├── MenuResponse.java              # Hierarchical menu node (with List<MenuResponse> submenus)
    │   │   │   ├── ParametrosSistemaResponse.java
    │   │   │   ├── PlazoHechuraAlhajaResponse.java
    │   │   │   ├── PlazoParametroResponse.java
    │   │   │   ├── PlazoResponse.java
    │   │   │   ├── RolResponse.java
    │   │   │   ├── SucursalResponse.java
    │   │   │   ├── TipoPrendaResponse.java
    │   │   │   ├── TurnoResponse.java
    │   │   │   └── UsuarioResponse.java
    │   │   │
    │   │   ├── service/                           # Business logic, @Transactional
    │   │   │   ├── BaseService.java               # Abstract; findAll, findById, save, update, delete, count
    │   │   │   ├── CatSubtipoPrendaService.java
    │   │   │   ├── ContratoService.java           # crearContrato(), getById(), getByFolio(), getContratosPorCliente(), getContratosVencidos()
    │   │   │   ├── CatValorPrendaService.java
    │   │   │   ├── CatalogoService.java
    │   │   │   ├── ClienteService.java
    │   │   │   ├── ConfiguracionService.java
    │   │   │   ├── EmpresaService.java
    │   │   │   ├── MasterDataService.java         # Returns hardcoded list of 32 Mexican states
    │   │   │   ├── ParametrosSistemaService.java
    │   │   │   ├── PlazoService.java
    │   │   │   ├── PrinterService.java            # Stub; ESC/POS printer planned, prints to stdout now
    │   │   │   ├── RolService.java
    │   │   │   ├── SucursalService.java           # @Cacheable("sucursal") on read methods
    │   │   │   ├── TipoPrendaService.java
    │   │   │   ├── TurnoService.java              # iniciarTurno, cerrarTurno, obtenerTurnoActivo
    │   │   │   └── UsuarioService.java            # login(), cambiarPassword(), filtrarOpcionesPorTurno()
    │   │   │
    │   │   └── util/                              # Utilities
    │   │       ├── Constantes.java                # String constants (menu names, config param keys)
    │   │       ├── ContratoTestMain.java          # Ad-hoc dev scratch class; not production
    │   │       ├── Encryptor.java                 # AES/ECB symmetric encryption (hardcoded key)
    │   │       ├── PagoExtemporaneoRow.java       # Row DTO for late-payment Jasper report
    │   │       ├── PagoRow.java                   # Row DTO for payment Jasper report
    │   │       └── PrendaRow.java                 # Row DTO for pledge-item Jasper report
    │   │       # Note: JwtUtil.java deleted in 260515-04c
    │   │
    │   └── resources/
    │       ├── application.properties             # DB, JPA/Hibernate, JDBC session, cookie, server port 8080
    │       ├── schema.sql                         # Generic schema DDL
    │       ├── schema-mariadb.sql                 # MariaDB-specific schema DDL (Spring Session tables)
    │       ├── jasper/                            # JasperReports .jrxml templates for PDF/print output
    │       └── db/changelog/changes/              # Liquibase changesets (001–010)
    │           # 001-initial-schema.sql           # Base schema
    │           # 002-initial-data.sql             # Seed data
    │           # 003-session-params.sql           # Session/cookie parameters
    │           # 004-search-indexes.sql           # FULLTEXT index on clientes (260514-x0j)
    │           # 005-drop-session-token-usuarios.sql  # Remove unused session_token column (260515-04c)
    │           # 006-plazos-sucursal.sql          # PlazoHechuraAlhaja + multi-sucursal plazos (260516-mns)
    │           # 007-contratos.sql                # Tables: contrato, partida_contrato, movimiento_contrato (260522-h4a)
    │           # 008-menu-avaluos.sql             # Opcion/menu entries for Avaluos (260522-h4a)
    │           # 009-clientes-prueba.sql          # Test client data
    │           # 010-plazo-parametro-incremento-avaluo.sql  # porcIncrementoAvaluo field (260519-wxy)
    │
    └── test/
        └── java/com/ignis/prestamil/
            └── PrestamilApplicationTests.java     # Minimal smoke test
```

---

## Frontend Structure

Root: `prestamil-frontend/`

```
prestamil-frontend/
├── angular.json                                   # CLI workspace config; build targets, asset paths
├── package.json                                   # Angular 20, ng-bootstrap, Bootstrap 5.3, ApexCharts, ngx-scrollbar
├── tsconfig.json                                  # TypeScript base config
├── tsconfig.app.json                              # App-specific TS config
├── eslint.config.mjs                             # ESLint flat config with Angular + TypeScript rules
└── src/
    ├── main.ts                                    # bootstrapApplication(AppComponent, appConfig)
    ├── index.html                                 # SPA HTML shell (single <app-root>)
    ├── polyfills.ts                               # Zone.js import
    │
    ├── environments/
    │   ├── environment.ts                         # Dev: apiUrl='http://localhost:8080', production=true
    │   └── environment.prod.ts                    # Prod overrides (swapped at ng build --configuration production)
    │
    ├── assets/                                    # Static files: images, icons, chart libraries
    ├── fake-data/                                 # Static mock data for prototyping
    ├── scss/                                      # Global SCSS: Bootstrap theme overrides, fonts, nav styles
    │
    └── app/
        ├── app-config.ts                          # ApplicationConfig: BrowserModule, routing, interceptors
        ├── app.component.ts                       # Root component; subscribes to router events for scroll reset
        ├── app.component.html                     # <app-spinner> + <router-outlet>
        ├── app-routing.module.ts                  # All route definitions; two shells: GuestComponent, AdminComponent
        │
        ├── prestamil/                             # === Business domain code ===
        │   │
        │   ├── core/                              # Cross-cutting concerns
        │   │   ├── guards/
        │   │   │   ├── auth.guard.ts              # CanActivateFn: blocks unauthenticated access; checks localStorage
        │   │   │   └── login.guard.ts             # CanActivateFn: redirects authenticated users away from /login
        │   │   │
        │   │   ├── helpers/
        │   │   │   └── menu-transformer.helper.ts # Converts flat OpcionMenu[] (backend) → NavigationItem[] (sidebar)
        │   │   │
        │   │   ├── interceptors/
        │   │   │   ├── credentials.interceptor.ts # Adds withCredentials:true to all requests to apiUrl
        │   │   │   └── auth-error.interceptor.ts  # Handles 401/403/440 → calls AuthService.handleSessionInvalidation()
        │   │   │
        │   │   ├── models/
        │   │   │   ├── auth-response.model.ts     # LoginResponse, OpcionMenu TypeScript interfaces
        │   │   │   ├── cliente.model.ts           # Cliente interface
        │   │   │   ├── contrato.model.ts          # ContratoRequest/Response, PartidaContratoRequest/Response interfaces
        │   │   │   ├── plazo.model.ts             # PlazoRequest/Response, PlazoParametro*, PlazoHechuraAlhaja* interfaces
        │   │   │   ├── turno.model.ts             # Turno interface {id, activo, fechaInicio, fechaFin, usuario}
        │   │   │   └── usuario.model.ts           # Usuario interface
        │   │   │
        │   │   └── services/
        │   │       ├── auth.service.ts            # Auth state (BehaviorSubject), login/logout, menu mgmt, localStorage
        │   │       ├── auth-stream.service.ts     # SSE client: EventSource, force-logout + turno-cerrado events, backoff
        │   │       ├── cliente.service.ts         # search() + GET /api/clientes + /api/clientes/search?q=
        │   │       ├── contrato.service.ts        # crearContrato(), getById(), getByClienteId(), etc.
        │   │       ├── prenda.service.ts          # getValoresPrenda(), getTipos(), getSubtipos()
        │   │       ├── rol.service.ts             # GET /api/roles
        │   │       ├── session-warning.service.ts # Session expiry warning notifications
        │   │       ├── turno.service.ts           # iniciar/cerrar/activo; BehaviorSubject<Turno|null>
        │   │       ├── plazo.service.ts           # CRUD plazos + parámetros multi-sucursal + tabla alhajas
        │   │       └── usuario.service.ts         # CRUD calls to /api/usuarios
        │   │
        │   ├── dashboard/
        │   │   ├── dashboard.component.ts         # Default landing page after login
        │   │   ├── dashboard.component.html
        │   │   ├── sse-status.component.ts        # Debug view: shows SSE connection status from AuthStreamService
        │   │   └── sse-status.component.html
        │   │
        │   └── pages/                             # Feature pages — all standalone, all lazy-loaded
        │       ├── authentication/
        │       │   └── login/
        │       │       ├── login.component.ts     # Login form; calls AuthService.login() then setSession()
        │       │       └── login.component.html
        │       │
        │       ├── catalogos/
        │       │   ├── descuentos/
        │       │   │   └── descuentos.component.ts  # Discount catalog management
        │       │   ├── empresas/
        │       │   │   └── empresas.component.ts    # Company catalog (also reachable via /configuracion/empresas)
        │       │   └── prendas/
        │       │       └── prendas.component.ts     # Pledge item type/subtype/value management
        │       │
        │       ├── clientes/
        │       │   └── clientes.component.ts        # Customer registry
        │       │
        │       ├── configuracion/
        │       │   ├── actualizar-password/
        │       │   │   └── actualizar-password.component.ts  # PUT /api/usuarios/{id}/cambiar-password
        │       │   ├── parametros-generales/
        │       │   │   └── parametros-generales.component.ts # System parameter management
        │       │   ├── parametros-prestamo/
        │       │   │   └── parametros-prestamo.component.ts  # Loan term parameters
        │       │   ├── plazos-periodos/
        │       │   │   └── plazos-periodos.component.ts      # Loan term definitions
        │       │   └── sucursal/
        │       │       └── sucursal.component.ts             # Branch info editor
        │       │
        │       ├── avaluos/
        │       │   └── avaluo/
        │       │       └── avaluo.component.ts      # Loan origination — real APIs (ClienteService, PrendaService, ContratoService); route /avaluos
        │       │
        │       ├── hardware/
        │       │   └── hardware.component.ts        # Hardware/printer config page
        │       │
        │       ├── turnos/
        │       │   └── turnos.component.ts          # Open/close shift with NgbModal confirmation; calls refreshMenuFromBackend
        │       │
        │       └── usuarios/
        │           └── usuarios.component.ts        # User CRUD with role assignment; uses ReactiveFormsModule
        │
        └── theme/                                   # UI shell (based on "Datta Able" admin template)
            ├── layout/
            │   ├── admin/
            │   │   ├── admin.component.ts           # Authenticated layout shell: sidebar + navbar + <router-outlet>
            │   │   ├── admin.component.html
            │   │   ├── configuration/               # Theme customization panel (colors, layout)
            │   │   ├── footer/                      # Footer bar
            │   │   ├── nav-bar/
            │   │   │   ├── nav-bar.component.ts     # Top navigation bar
            │   │   │   ├── nav-left/                # Left section: search input
            │   │   │   └── nav-right/               # Right section: user dropdown, logout button
            │   │   └── navigation/
            │   │       ├── navigation.ts            # NavigationItem interface (id, title, type, url, icon, children)
            │   │       ├── navigation.component.ts  # Sidebar component; reads menuItems$ from AuthService
            │   │       └── nav-content/
            │   │           ├── nav-collapse/        # Collapsible menu group
            │   │           ├── nav-group/           # Group header (e.g., "Catalogos")
            │   │           ├── nav-item/            # Leaf link item
            │   │           └── nav-logo/            # Logo in sidebar header
            │   └── guest/
            │       ├── guest.component.ts           # Unauthenticated layout wrapper (login page)
            │       └── guest.component.html
            │
            └── shared/
                ├── shared.module.ts                 # NgModule re-exporting: CommonModule, FormsModule,
                │                                    # ReactiveFormsModule, CardComponent, NgbModule, NgScrollbarModule
                ├── components/
                │   ├── breadcrumbs/                 # Breadcrumb navigation bar
                │   ├── card/                        # Card wrapper (standalone component)
                │   ├── full-screen/                 # Fullscreen toggle utility
                │   └── spinner/                     # Loading overlay (shown during isLoggingOut$)
                ├── directive/                       # Shared Angular directives
                ├── filters/                         # Shared Angular pipes
                ├── helpers/                         # Theme helper utilities
                └── service/                         # Theme-level services (nav state, etc.)
```

---

## Key Files

| File | Purpose |
|------|---------|
| `prestamil-backend/src/main/java/com/ignis/prestamil/PrestamilApplication.java` | Spring Boot entry point |
| `prestamil-backend/src/main/java/com/ignis/prestamil/config/SecurityConfig.java` | Security filter chain: session auth, CSRF off, 1-session-per-user, logout URL |
| `prestamil-backend/src/main/java/com/ignis/prestamil/config/CorsConfig.java` | CORS: allows `http://localhost:4200` with credentials |
| `prestamil-backend/src/main/java/com/ignis/prestamil/config/SessionConfig.java` | JDBC session registry; `@EnableJdbcHttpSession` |
| `prestamil-backend/src/main/java/com/ignis/prestamil/config/CacheConfig.java` | Caffeine cache manager for `sucursal` (30-min TTL) |
| `prestamil-backend/src/main/java/com/ignis/prestamil/controller/AuthController.java` | Login: validates creds, creates session, invalidates old sessions |
| `prestamil-backend/src/main/java/com/ignis/prestamil/controller/LogoutStreamController.java` | SSE: `ConcurrentHashMap<username, SseEmitter>`; broadcasts `force-logout` and `turno-cerrado` |
| `prestamil-backend/src/main/java/com/ignis/prestamil/controller/TurnoController.java` | Shift open/close; broadcasts SSE to all connected users on close |
| `prestamil-backend/src/main/java/com/ignis/prestamil/service/BaseService.java` | Abstract CRUD service template |
| `prestamil-backend/src/main/java/com/ignis/prestamil/service/UsuarioService.java` | Login logic, menu filtering by turno state, password change |
| `prestamil-backend/src/main/java/com/ignis/prestamil/service/TurnoService.java` | Shift lifecycle; reads auth principal from `SecurityContextHolder` |
| `prestamil-backend/src/main/java/com/ignis/prestamil/util/Encryptor.java` | AES/ECB symmetric encrypt/decrypt; hardcoded key |
| `prestamil-backend/src/main/java/com/ignis/prestamil/util/Constantes.java` | String constants for menu names and config param keys |
| `prestamil-backend/src/main/java/com/ignis/prestamil/exception/GlobalExceptionHandler.java` | `@ControllerAdvice`; maps all exceptions to `ErrorResponse` JSON |
| `prestamil-backend/src/main/resources/application.properties` | DB datasource, JPA dialect, session config, cookie settings |
| `prestamil-backend/src/main/resources/schema.sql` | Database DDL |
| `prestamil-frontend/src/app/app-config.ts` | Root `ApplicationConfig`; HTTP interceptor registration |
| `prestamil-frontend/src/app/app-routing.module.ts` | All route definitions; lazy `loadComponent` for every feature page |
| `prestamil-frontend/src/app/prestamil/core/services/auth.service.ts` | Central auth: login, logout, `localStorage`, menu, `BehaviorSubject` state |
| `prestamil-frontend/src/app/prestamil/core/services/auth-stream.service.ts` | SSE client: `EventSource` lifecycle, exponential backoff reconnection |
| `prestamil-frontend/src/app/prestamil/core/services/turno.service.ts` | Shift calls; `currentTurno$: BehaviorSubject<Turno|null>` |
| `prestamil-frontend/src/app/prestamil/core/interceptors/credentials.interceptor.ts` | Adds `withCredentials: true` to all requests to `apiUrl` |
| `prestamil-frontend/src/app/prestamil/core/interceptors/auth-error.interceptor.ts` | 401/403/440 → `handleSessionInvalidation()` |
| `prestamil-frontend/src/app/prestamil/core/helpers/menu-transformer.helper.ts` | `OpcionMenu[]` → `NavigationItem[]` for sidebar rendering |
| `prestamil-frontend/src/app/prestamil/core/guards/auth.guard.ts` | Blocks unauthenticated access to all `AdminComponent` children |
| `prestamil-frontend/src/app/prestamil/core/guards/login.guard.ts` | Blocks `/login` for already-authenticated users |
| `prestamil-frontend/src/environments/environment.ts` | `apiUrl: 'http://localhost:8080'` |

---

## Naming Conventions

**Backend:**
- Entity classes: PascalCase noun matching the DB table concept (`Usuario`, `TipoPrenda`, `PlazoParametro`)
- Repository: `<Entity>Repository.java`
- Service: `<Entity>Service.java`
- Controller: `<Entity>Controller.java` or `<Domain>Controller.java`
- Mapper: `<Entity>Mapper.java`
- Request DTOs: `<Entity>Request.java` or `<Action>Request.java`
- Response DTOs: `<Entity>Response.java`
- Composite keys: `<Entity>Id.java`

**Frontend:**
- Feature components: `<feature>.component.ts` / `.html` / `.scss`
- Services: `<domain>.service.ts`
- Guards: `<name>.guard.ts`
- Interceptors: `<name>.interceptor.ts`
- Models: `<name>.model.ts`
- Helpers: `<name>.helper.ts`

---

## Where to Add New Code

**New backend entity/feature:**
1. `model/NewEntity.java` — JPA `@Entity` with Lombok
2. `repository/NewEntityRepository.java` — extend `BaseRepository<NewEntity, Integer>`
3. `service/NewEntityService.java` — extend `BaseService<NewEntity, Integer, NewEntityRepository>`
4. `request/NewEntityRequest.java` — inbound DTO with `@Valid` annotations
5. `response/NewEntityResponse.java` — outbound DTO
6. `mapper/NewEntityMapper.java` — MapStruct `@Mapper(componentModel = "spring")`
7. `controller/NewEntityController.java` — `@RestController @RequestMapping("/api/new-entities")`

**New frontend feature page:**
1. Create `src/app/prestamil/pages/<domain>/<feature>/<feature>.component.ts` (standalone, import `CommonModule`, `SharedModule`)
2. Create matching `.html` template
3. Register in `src/app/app-routing.module.ts` under `AdminComponent` children with `loadComponent`
4. Add `Opcion` + `RolOpcion` DB records so the menu item appears for the correct role

**New Angular service:**
- Domain services: `src/app/prestamil/core/services/<domain>.service.ts`
- Theme/UI services: `src/app/theme/shared/service/<name>.service.ts`

**New shared UI component:**
- Add to `src/app/theme/shared/components/<name>/`
- Export from `SharedModule` in `src/app/theme/shared/shared.module.ts`

**New backend exception type:**
- Add `<Name>Exception.java` in `exception/` extending `RuntimeException`
- Add an `@ExceptionHandler` case in `GlobalExceptionHandler.java`

---

*Structure analysis: 2026-05-22*
