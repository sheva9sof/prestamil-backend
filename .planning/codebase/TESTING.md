# Testing

**Analysis Date:** 2026-05-12

---

## Backend Testing

### Framework & Dependencies

**Runner:** JUnit 5 (Jupiter) via `spring-boot-starter-test` (bundles JUnit Jupiter, Mockito, AssertJ, Spring Test, Spring Boot Test)

**Declaration in `pom.xml`:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

**Run commands:**
```bash
./mvnw test       # run all unit tests
./mvnw verify     # run tests + integration lifecycle phase
```

### What Is Tested

One test file exists in the entire backend:

**`prestamil-backend/src/test/java/com/ignis/prestamil/PrestamilApplicationTests.java`**
```java
@SpringBootTest
class PrestamilApplicationTests {
    @Test
    void contextLoads() {
        // verifies the Spring application context starts without errors
    }
}
```

This is a smoke test only. No service logic, controller behavior, mapper correctness, exception handling, or repository queries are tested.

### Test Directory Structure

```
prestamil-backend/src/test/
└── java/
    └── com/ignis/prestamil/
        └── PrestamilApplicationTests.java   ← only test file in the project
```

---

## Frontend Testing

### Framework & Dependencies

**Runner:** Karma + Jasmine (Angular default)
- `@angular/cli` includes Karma/Jasmine integration via `ng test`
- `tsconfig.spec.json` includes `"types": ["jasmine"]`
- No `karma.conf.js` found — Angular CLI default config is used

**No Jest, no Cypress, no Playwright.**

**Run commands:**
```bash
npm test          # ng test  (Karma + Jasmine)
npm run lint      # ng lint  (ESLint check)
npm run lint:fix  # ng lint --fix
npm run prettier  # prettier --write ./src
```

### What Is Tested

**Zero `.spec.ts` files exist** anywhere under `prestamil-frontend/src/`. The test infrastructure (`tsconfig.spec.json`, `"test": "ng test"` script) is configured but no tests have been written.

---

## Coverage

| Layer                         | Files                          | Tests |
|-------------------------------|--------------------------------|-------|
| Backend — services            | 15+ service classes            | 0     |
| Backend — controllers         | 10+ controllers                | 0     |
| Backend — mappers             | 10+ mapper classes             | 0     |
| Backend — exception handling  | `GlobalExceptionHandler`       | 0     |
| Backend — app context         | `PrestamilApplicationTests`    | 1     |
| Frontend — components         | 15+ page components            | 0     |
| Frontend — services           | 5 services                     | 0     |
| Frontend — interceptors       | 2 interceptors                 | 0     |
| Frontend — guards             | 2 guards                       | 0     |

**Effective test coverage: ~0%.** The single backend test verifies context startup only.

---

## Test Patterns (What to Follow When Writing Tests)

Because no tests have been written yet, the following patterns match Spring Boot 3 / Angular 20 conventions used by the rest of the codebase and should be followed for any new tests.

### Backend — Unit Test (Service with Mockito)

```java
package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.model.Turno;
import com.ignis.prestamil.repository.TurnoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TurnoServiceTest {

    @Mock
    private TurnoRepository turnoRepository;

    @InjectMocks
    private TurnoService turnoService;

    @Test
    void cerrarTurno_whenTurnoNotFound_throwsResourceNotFoundException() {
        when(turnoRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> turnoService.cerrarTurno(99))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("99");
    }
}
```

**Naming:** `<ClassUnderTest>Test` suffix; test method name: `<method>_<condition>_<expectedBehavior>`.

### Backend — Controller Slice Test (`@WebMvcTest`)

```java
@WebMvcTest(ClienteController.class)
class ClienteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClienteService clienteService;

    @MockBean
    private ClienteMapper clienteMapper;

    @Test
    void findById_whenNotFound_returns404() throws Exception {
        when(clienteService.findById(99))
            .thenThrow(new ResourceNotFoundException("Recurso no encontrado con id: 99"));

        mockMvc.perform(get("/api/clientes/99"))
            .andExpect(status().isNotFound());
    }
}
```

### Backend — Integration Test (`@SpringBootTest`)

Use a separate `application-test.properties` in `src/test/resources/` with an H2 in-memory datasource for full-stack integration tests:
```properties
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=create-drop
```

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EmpresaIntegrationTest {
    @Autowired
    private TestRestTemplate restTemplate;
    // ...
}
```

### Frontend — Service Unit Test (Jasmine + HttpClientTestingModule)

```typescript
// auth.service.spec.ts  (co-located with auth.service.ts)
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [AuthService]
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should call login endpoint', () => {
    service.login('user', 'pass').subscribe();
    const req = httpMock.expectOne(req => req.url.includes('/auth/login'));
    expect(req.request.method).toBe('POST');
    req.flush({});
  });
});
```

### Frontend — Component Test (`TestBed`)

```typescript
// turnos.component.spec.ts  (co-located with turnos.component.ts)
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TurnosComponent } from './turnos.component';
import { TurnoService } from '../../core/services/turno.service';
import { AuthService } from '../../core/services/auth.service';
import { of } from 'rxjs';

describe('TurnosComponent', () => {
  let component: TurnosComponent;
  let fixture: ComponentFixture<TurnosComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TurnosComponent],
      providers: [
        { provide: TurnoService, useValue: { currentTurno$: of(null), refreshActivo: () => {} } },
        { provide: AuthService, useValue: { getUser: () => ({ idRol: 5 }), isLoggingOut$: of(false) } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(TurnosComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
```

---

## Coverage Gaps & Priority Areas

### Highest Risk — Backend Services (No Tests)

**`TurnoService`** — `src/main/java/com/ignis/prestamil/service/TurnoService.java`
- `iniciarTurno()`: validates no active turno exists, retrieves authenticated user from `SecurityContext`, creates turno
- `cerrarTurno()`: validates turno is active, sets `fechaFin`, marks inactive
- `obtenerTurnoActivo()`: returns `null` if no active turno found

**`BaseService`** — `src/main/java/com/ignis/prestamil/service/BaseService.java`
- `findById()`: throws `ResourceNotFoundException` when not found
- `deleteById()`: checks existence before deleting, throws on missing

**`ClienteService`** — `src/main/java/com/ignis/prestamil/service/ClienteService.java`
- `loadDireccion()`: conditionally loads address from repository by ID
- `searchByNombreCompletoOrTelefono()`: delegates to repository query

**`GlobalExceptionHandler`** — `src/main/java/com/ignis/prestamil/exception/GlobalExceptionHandler.java`
- All three `@ExceptionHandler` methods produce correct `ErrorResponse` structure

### High Risk — Backend Controllers (No Tests)

All controllers in `src/main/java/com/ignis/prestamil/controller/` are untested:
- `UsuarioController` — `/api/usuarios/me`, `/buscar`, `/cambiar-password`
- `TurnoController` — `/iniciar`, `/cerrar/{id}` trigger SSE events
- `ClienteController` — `/search?q=` endpoint behavior

### High Risk — Frontend Auth Flow (No Tests)

**`AuthService`** — `src/app/prestamil/core/services/auth.service.ts`
- `setSession()`, `logout()`, `handleSessionInvalidation()`, `refreshMenuFromBackend()`

**`AuthErrorInterceptor`** — `src/app/prestamil/core/interceptors/auth-error.interceptor.ts`
- 401/403/440 interception triggers `handleSessionInvalidation()`

**`authGuard`** — `src/app/prestamil/core/guards/auth.guard.ts`
- Redirects unauthenticated to `/login`

### Medium Risk — Frontend Component Logic (No Tests)

**`UsuariosComponent`** — `src/app/prestamil/pages/usuarios/usuarios.component.ts`
- `optionalMinLength()` custom validator logic
- `aplicarFiltros()` filtering by role and status
- `handleSaveError()` duplicate username detection

**`TurnosComponent`** — `src/app/prestamil/pages/turnos/turnos.component.ts`
- Role-based `puedeAbrir` logic in `ngOnInit`
- Modal confirmation flow in `iniciar()` / `cerrar()`

---

## File Placement for New Tests

**Backend:** Mirror source package under `src/test/java/`:
```
src/test/java/com/ignis/prestamil/
├── service/
│   ├── TurnoServiceTest.java
│   └── BaseServiceTest.java
├── controller/
│   └── ClienteControllerTest.java
└── exception/
    └── GlobalExceptionHandlerTest.java
```

**Frontend:** Co-locate spec files next to source files:
```
src/app/prestamil/core/services/
├── auth.service.ts
├── auth.service.spec.ts      ← new
├── turno.service.ts
└── turno.service.spec.ts     ← new

src/app/prestamil/pages/turnos/
├── turnos.component.ts
└── turnos.component.spec.ts  ← new
```
