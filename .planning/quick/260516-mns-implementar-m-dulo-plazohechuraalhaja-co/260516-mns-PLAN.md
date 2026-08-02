---
phase: quick-260516-mns
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  # Backend — migration
  - prestamil-backend/src/main/resources/db/changelog/changes/006-plazos-sucursal.sql
  - prestamil-backend/src/main/resources/db/changelog/db.changelog-master.xml
  # Backend — model
  - prestamil-backend/src/main/java/com/ignis/prestamil/model/PlazoParametro.java
  - prestamil-backend/src/main/java/com/ignis/prestamil/model/PlazoParametroId.java
  - prestamil-backend/src/main/java/com/ignis/prestamil/model/PlazoHechuraAlhaja.java
  - prestamil-backend/src/main/java/com/ignis/prestamil/model/PlazoHechuraAlhajaId.java
  # Backend — repository
  - prestamil-backend/src/main/java/com/ignis/prestamil/repository/PlazoParametroRepository.java
  - prestamil-backend/src/main/java/com/ignis/prestamil/repository/PlazoHechuraAlhajaRepository.java
  # Backend — DTOs
  - prestamil-backend/src/main/java/com/ignis/prestamil/request/PlazoHechuraAlhajaRequest.java
  - prestamil-backend/src/main/java/com/ignis/prestamil/response/PlazoHechuraAlhajaResponse.java
  # Backend — mapper
  - prestamil-backend/src/main/java/com/ignis/prestamil/mapper/PlazoHechuraAlhajaMapper.java
  # Backend — service + controller
  - prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java
  - prestamil-backend/src/main/java/com/ignis/prestamil/controller/PlazoController.java
  # Frontend
  - prestamil-frontend/src/app/prestamil/core/models/plazo.model.ts
  - prestamil-frontend/src/app/prestamil/core/services/plazo.service.ts
  - prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.ts
  - prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.html
  - prestamil-frontend/src/app/prestamil/pages/configuracion/parametros-prestamo/parametros-prestamo.component.ts
  - prestamil-frontend/src/app/prestamil/pages/configuracion/parametros-prestamo/parametros-prestamo.component.html
  # Documentation
  - .planning/codebase/ARCHITECTURE.md
  - .planning/codebase/CONCERNS.md
  - .planning/codebase/STRUCTURE.md
autonomous: true
requirements:
  - PLAZO-01  # Migración SQL multi-sucursal
  - PLAZO-02  # Entidad PlazoHechuraAlhaja completa
  - PLAZO-03  # PlazoParametro con sucursalId en clave compuesta
  - PLAZO-04  # PlazoService — bug fix + métodos para alhajas y sucursal
  - PLAZO-05  # Endpoints REST nuevos (parámetros/alhajas con sucursalId)
  - PLAZO-06  # Frontend service y modelos
  - PLAZO-07  # Refactor plazos-periodos a layout dos paneles + tabs
  - PLAZO-08  # Implementar parametros-prestamo (vista solo lectura)
  - DOC-01    # Actualizar ARCHITECTURE.md, CONCERNS.md, STRUCTURE.md

must_haves:
  truths:
    - "Al ejecutar mvn clean compile el backend compila sin errores"
    - "Liquibase aplica el changeset 006-plazos-sucursal.sql sin fallos al startup"
    - "GET /api/plazos/{id}/parametros?sucursalId=1 devuelve lista de PlazoParametro filtrada por sucursal"
    - "PUT /api/plazos/{id}/parametros/{tipoPrendaId}?sucursalId=1 inserta o actualiza (upsert) el registro"
    - "GET /api/plazos/{id}/alhajas?sucursalId=1 devuelve la tabla de hechuras por kilataje y hechura"
    - "PUT /api/plazos/{id}/alhajas/{kilataje}/{hechura}?sucursalId=1 recalcula precioPrestamo = precioBase * (1 + porcAumento)"
    - "PUT /api/plazos/{id}/alhajas/precio-oro?sucursalId=1 recalcula todos los precios usando fórmula (precioBaseOro / 24) * kilataje * 31.1035"
    - "getParametrosPlazo() lanza ResourceNotFoundException en vez de devolver null"
    - "Frontend plazos-periodos.component muestra layout dos paneles con tabs Parámetros / Alhajas"
    - "Frontend parametros-prestamo.component muestra parámetros agrupados por plazo en modo solo lectura"
  artifacts:
    - path: "prestamil-backend/src/main/resources/db/changelog/changes/006-plazos-sucursal.sql"
      provides: "Migración Liquibase agregando sucursal_id a plazo_parametro y plazo_hechura_alhaja"
      contains: "ALTER TABLE plazo_parametro"
    - path: "prestamil-backend/src/main/java/com/ignis/prestamil/model/PlazoHechuraAlhaja.java"
      provides: "Entidad JPA para tabla plazo_hechura_alhaja con @EmbeddedId"
      contains: "@EmbeddedId"
    - path: "prestamil-backend/src/main/java/com/ignis/prestamil/model/PlazoHechuraAlhajaId.java"
      provides: "Clave compuesta (idPlazo, sucursalId, kilataje, hechura) implementando Serializable"
      contains: "implements Serializable"
    - path: "prestamil-backend/src/main/java/com/ignis/prestamil/repository/PlazoHechuraAlhajaRepository.java"
      provides: "Repositorio JPA con findByIdIdPlazoAndIdSucursalId"
      contains: "findByIdIdPlazoAndIdSucursalId"
    - path: "prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java"
      provides: "Lógica de negocio para parámetros y alhajas con multi-sucursal"
      contains: "actualizarTodosPrecios"
    - path: "prestamil-backend/src/main/java/com/ignis/prestamil/controller/PlazoController.java"
      provides: "5 endpoints nuevos para parámetros/alhajas con sucursalId"
      contains: "/alhajas/precio-oro"
    - path: "prestamil-frontend/src/app/prestamil/core/services/plazo.service.ts"
      provides: "Servicio Angular con métodos HTTP para todos los endpoints"
      contains: "actualizarTodosPrecios"
    - path: "prestamil-frontend/src/app/prestamil/pages/configuracion/parametros-prestamo/parametros-prestamo.component.ts"
      provides: "Componente solo-lectura con vista de parámetros agrupados por plazo"
      min_lines: 50
  key_links:
    - from: "prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java"
      to: "PlazoHechuraAlhajaRepository"
      via: "constructor injection con @RequiredArgsConstructor"
      pattern: "plazoHechuraAlhajaRepository"
    - from: "prestamil-backend/src/main/java/com/ignis/prestamil/controller/PlazoController.java"
      to: "PlazoService"
      via: "5 endpoints nuevos (GET/PUT /parametros, GET/PUT /alhajas, PUT /alhajas/precio-oro)"
      pattern: "@RequestParam.*sucursalId"
    - from: "prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.ts"
      to: "PlazoService"
      via: "inject(PlazoService) y suscripciones a getTablaAlhajas / actualizarTodosPrecios"
      pattern: "inject\\(PlazoService\\)"
    - from: "prestamil-frontend/src/app/prestamil/pages/configuracion/parametros-prestamo/parametros-prestamo.component.ts"
      to: "PlazoService"
      via: "inject(PlazoService) — llamadas a getAll y getParametrosBySucursal"
      pattern: "inject\\(PlazoService\\)"
---

<objective>
Implementar el módulo PlazoHechuraAlhaja completo (backend + frontend) y completar el módulo Plazos con soporte multi-sucursal.

Purpose: La configuración de plazos y parámetros de préstamo actualmente NO soporta múltiples sucursales — todos los plazos comparten una sola configuración. Además, no existe la tabla de hechuras de alhajas (oro, plata) necesaria para calcular precios de empeño por kilataje. Este plan agrega sucursal_id como columna y como parte de la clave compuesta, crea la entidad PlazoHechuraAlhaja completa con su fórmula de cálculo de precios, y completa los componentes Angular pendientes.

Output:
- Migración Liquibase 006 con ALTER TABLE para sucursal_id
- 4 archivos de modelo (PlazoHechuraAlhaja + Id, modificaciones a PlazoParametro + Id)
- 1 repositorio nuevo + modificaciones al repositorio de PlazoParametro
- 2 DTOs nuevos (Request/Response) + 1 mapper manual
- Modificaciones a PlazoService (bug fix + 5 métodos nuevos)
- 5 endpoints nuevos en PlazoController
- Frontend: model + service + 2 componentes (uno refactorizado, uno implementado desde cero)
- Documentación actualizada (ARCHITECTURE, CONCERNS, STRUCTURE)
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@./CLAUDE.md
@.planning/STATE.md
@.planning/codebase/ARCHITECTURE.md
@.planning/codebase/CONCERNS.md
@.planning/codebase/STRUCTURE.md

# Archivos existentes que serán modificados o referenciados como patrón
@prestamil-backend/src/main/java/com/ignis/prestamil/model/Plazo.java
@prestamil-backend/src/main/java/com/ignis/prestamil/model/PlazoParametro.java
@prestamil-backend/src/main/java/com/ignis/prestamil/model/PlazoParametroId.java
@prestamil-backend/src/main/java/com/ignis/prestamil/model/RolOpcionId.java
@prestamil-backend/src/main/java/com/ignis/prestamil/repository/PlazoParametroRepository.java
@prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java
@prestamil-backend/src/main/java/com/ignis/prestamil/controller/PlazoController.java
@prestamil-backend/src/main/java/com/ignis/prestamil/mapper/PlazoMapper.java
@prestamil-backend/src/main/java/com/ignis/prestamil/mapper/PlazoParametroMapper.java
@prestamil-backend/src/main/java/com/ignis/prestamil/request/PlazoRequest.java
@prestamil-backend/src/main/java/com/ignis/prestamil/request/PlazoParametroRequest.java
@prestamil-backend/src/main/java/com/ignis/prestamil/response/PlazoResponse.java
@prestamil-backend/src/main/java/com/ignis/prestamil/response/PlazoParametroResponse.java
@prestamil-backend/src/main/resources/db/changelog/changes/004-search-indexes.sql
@prestamil-backend/src/main/resources/db/changelog/db.changelog-master.xml
@prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.ts
@prestamil-frontend/src/app/prestamil/pages/configuracion/parametros-prestamo/parametros-prestamo.component.ts
@prestamil-frontend/src/app/prestamil/core/services/usuario.service.ts
@prestamil-frontend/src/app/prestamil/core/models/usuario.model.ts

<interfaces>
<!-- Contratos clave que los tasks deben respetar. Extraídos del codebase. -->

# Backend — PlazoParametroId (ACTUAL, a modificar)
```java
@Embeddable
public class PlazoParametroId implements Serializable {
    private Long plazoId;       // <-- existente
    private Integer tipoPrendaId; // <-- existente
    // AGREGAR: private Integer sucursalId;
}
```

# Backend — patrón de mapper manual (PlazoMapper.java)
```java
@Component
public class PlazoMapper {
    public PlazoResponse toPlazoResponse(Plazo plazo) { /* manual */ }
    public Plazo toPlazo(PlazoRequest request) { /* manual */ }
}
// NO usar @Mapper(componentModel="spring"); el proyecto usa @Component
```

# Backend — patrón de servicio (BaseService)
```java
@Service
@Transactional
@RequiredArgsConstructor
public class PlazoService extends BaseService<Plazo, Long, PlazoRepository> {
    // Métodos custom con Javadoc en español
}
```

# Backend — patrón de controlador (estructura existente PlazoController)
```java
@RestController
@RequestMapping("/api/plazos")
@RequiredArgsConstructor
public class PlazoController { /* ResponseEntity<T> + ResourceNotFoundException */ }
```

# Frontend — patrón de servicio (usuario.service.ts)
```typescript
@Injectable({ providedIn: 'root' })
export class UsuarioService {
  private readonly http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/api/usuarios`;
  // métodos retornan Observable<T>
}
```

# Frontend — patrón de modelo (usuario.model.ts) — interfaces sin prefijo I
```typescript
export interface Usuario { /* PascalCase, camelCase fields */ }
```
</interfaces>
</context>

<tasks>

<!-- ========================================== -->
<!-- WAVE 1: Database migration                 -->
<!-- ========================================== -->

<task type="auto">
  <name>Task 1: Crear migración Liquibase 006 con sucursal_id en plazo_parametro y plazo_hechura_alhaja</name>
  <files>
    prestamil-backend/src/main/resources/db/changelog/changes/006-plazos-sucursal.sql,
    prestamil-backend/src/main/resources/db/changelog/db.changelog-master.xml
  </files>
  <action>
    Crear el archivo `006-plazos-sucursal.sql` con formato `--liquibase formatted sql` (mismo patrón que `001-initial-schema.sql`).

    Contenido EXACTO:
    ```sql
    --liquibase formatted sql

    --changeset emm-a:006-1
    --comment: Agregar sucursal_id a plazo_parametro (default=1 para datos existentes)
    ALTER TABLE plazo_parametro
      ADD COLUMN IF NOT EXISTS sucursal_id INT NOT NULL DEFAULT 1;

    --changeset emm-a:006-2
    --comment: Reemplazar PK de plazo_parametro para incluir sucursal_id
    ALTER TABLE plazo_parametro
      DROP PRIMARY KEY,
      ADD PRIMARY KEY (plazo_id, tipo_prenda_id, sucursal_id);

    --changeset emm-a:006-3
    --comment: FK plazo_parametro.sucursal_id -> sucursal.id
    ALTER TABLE plazo_parametro
      ADD CONSTRAINT fk_pp_sucursal FOREIGN KEY (sucursal_id) REFERENCES sucursal(id);

    --changeset emm-a:006-4
    --comment: Agregar sucursal_id a plazo_hechura_alhaja
    ALTER TABLE plazo_hechura_alhaja
      ADD COLUMN IF NOT EXISTS sucursal_id INT NOT NULL DEFAULT 1;

    --changeset emm-a:006-5
    --comment: FK plazo_hechura_alhaja.sucursal_id -> sucursal.id
    ALTER TABLE plazo_hechura_alhaja
      ADD CONSTRAINT fk_pha_sucursal FOREIGN KEY (sucursal_id) REFERENCES sucursal(id);

    --changeset emm-a:006-6
    --comment: Índice compuesto para búsquedas por sucursal+plazo
    CREATE INDEX IF NOT EXISTS idx_pha_sucursal ON plazo_hechura_alhaja(sucursal_id, id_plazo);
    ```

    IMPORTANTE: Antes de ejecutar el changeset 006-2 (DROP PRIMARY KEY) hay que verificar si hay un constraint FK relacionado al PK actual (plazo_id, tipo_prenda_id) que apunte hacia `plazo_prenda` u otra tabla. Si lo hay, agregar un changeset 006-1b INTERMEDIO que haga DROP del FK y luego 006-2c que lo recree apuntando a la nueva PK. Revisar `001-initial-schema.sql` para identificar cualquier constraint nombrado `fk_pp_*` antes de proceder. Si no existe FK saliente, los 6 changesets de arriba son suficientes.

    Después de crear el archivo, registrar el changeset en `db.changelog-master.xml`:
    - Leer el archivo
    - Agregar `<include file="changes/006-plazos-sucursal.sql" relativeToChangelogFile="true"/>` después del include de `005-drop-session-token-usuarios.sql`
  </action>
  <verify>
    <automated>cd prestamil-backend &amp;&amp; ./mvnw.cmd liquibase:status -Dliquibase.contexts=test 2>&amp;1 | grep -i "006-plazos-sucursal" || echo "Verificación manual: changeset listado en master xml"</automated>
  </verify>
  <done>
    - Archivo `006-plazos-sucursal.sql` creado con 6 changesets (o 7-8 si requiere drop/recreate de FK existente)
    - `db.changelog-master.xml` incluye el nuevo archivo
    - Sin errores de sintaxis SQL
  </done>
</task>

<!-- ========================================== -->
<!-- WAVE 2: Backend models (depende de Wave 1) -->
<!-- ========================================== -->

<task type="auto">
  <name>Task 2: Modificar PlazoParametro/PlazoParametroId + crear PlazoHechuraAlhaja/Id</name>
  <files>
    prestamil-backend/src/main/java/com/ignis/prestamil/model/PlazoParametro.java,
    prestamil-backend/src/main/java/com/ignis/prestamil/model/PlazoParametroId.java,
    prestamil-backend/src/main/java/com/ignis/prestamil/model/PlazoHechuraAlhaja.java,
    prestamil-backend/src/main/java/com/ignis/prestamil/model/PlazoHechuraAlhajaId.java
  </files>
  <action>
    **2.a — Modificar PlazoParametroId.java:**
    Agregar campo `private Integer sucursalId;` a la clase. Actualizar `equals()` y `hashCode()` para incluir `sucursalId`. Mantener `@Embeddable` + `implements Serializable` + Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor`.

    Nuevo constructor de 3 args: `public PlazoParametroId(Long plazoId, Integer tipoPrendaId, Integer sucursalId)`.

    **2.b — Modificar PlazoParametro.java:**
    Agregar campo:
    ```java
    @Column(name = "sucursal_id", insertable = false, updatable = false)
    private Integer sucursalId;
    ```
    (insertable/updatable=false porque ya forma parte del @EmbeddedId).

    NO usar @Data — usar @Getter @Setter (regla del proyecto).

    **2.c — Crear PlazoHechuraAlhajaId.java:**
    ```java
    package com.ignis.prestamil.model;

    import jakarta.persistence.Column;
    import jakarta.persistence.Embeddable;
    import lombok.*;

    import java.io.Serializable;
    import java.util.Objects;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public class PlazoHechuraAlhajaId implements Serializable {

        @Column(name = "id_plazo", nullable = false)
        private Integer idPlazo;

        @Column(name = "sucursal_id", nullable = false)
        private Integer sucursalId;

        @Column(name = "kilataje", nullable = false)
        private Integer kilataje;

        @Column(name = "hechura", nullable = false, length = 1)
        private String hechura; // "F" fina, "N" normal, "E" especial

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PlazoHechuraAlhajaId that)) return false;
            return Objects.equals(idPlazo, that.idPlazo)
                && Objects.equals(sucursalId, that.sucursalId)
                && Objects.equals(kilataje, that.kilataje)
                && Objects.equals(hechura, that.hechura);
        }

        @Override
        public int hashCode() { return Objects.hash(idPlazo, sucursalId, kilataje, hechura); }
    }
    ```

    **2.d — Crear PlazoHechuraAlhaja.java:**
    ```java
    package com.ignis.prestamil.model;

    import jakarta.persistence.*;
    import lombok.*;

    import java.math.BigDecimal;

    @Entity
    @Table(name = "plazo_hechura_alhaja")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public class PlazoHechuraAlhaja {

        @EmbeddedId
        private PlazoHechuraAlhajaId id;

        @Column(name = "tabla_prestamo_id", nullable = false)
        private Integer tablaPrestamoId;

        @Column(name = "precio_base", nullable = false, precision = 12, scale = 4)
        private BigDecimal precioBase;

        @Column(name = "porc_aumento", nullable = false, precision = 5, scale = 4)
        private BigDecimal porcAumento;

        @Column(name = "precio_prestamo", nullable = false, precision = 12, scale = 4)
        private BigDecimal precioPrestamo;
    }
    ```

    NOTAS CRÍTICAS:
    - Usar `@EmbeddedId` (no `@IdClass`) — patrón del proyecto.
    - Todos los `@Column` deben tener `name` explícito (regla del proyecto).
    - NO incluir `@Column(name="id_plazo")` separado en la entidad: ya está en el @EmbeddedId. Hibernate lo mapea automáticamente.
    - Sin relación @ManyToOne a Plazo en esta iteración (mantener simple).
  </action>
  <verify>
    <automated>cd prestamil-backend &amp;&amp; ./mvnw.cmd compile -q -pl . 2>&amp;1 | tail -30</automated>
  </verify>
  <done>
    - PlazoParametroId con campo sucursalId + equals/hashCode actualizados
    - PlazoParametro con campo sucursalId (insertable=false, updatable=false)
    - PlazoHechuraAlhajaId existe con 4 campos (@EmbeddedId)
    - PlazoHechuraAlhaja existe con tablaPrestamoId, precioBase, porcAumento, precioPrestamo
    - mvn compile pasa sin errores
  </done>
</task>

<!-- ============================================ -->
<!-- WAVE 3: Repositorios + DTOs + Mapper          -->
<!-- ============================================ -->

<task type="auto">
  <name>Task 3: Crear repositorio PlazoHechuraAlhaja, actualizar PlazoParametroRepository, crear DTOs y mapper</name>
  <files>
    prestamil-backend/src/main/java/com/ignis/prestamil/repository/PlazoHechuraAlhajaRepository.java,
    prestamil-backend/src/main/java/com/ignis/prestamil/repository/PlazoParametroRepository.java,
    prestamil-backend/src/main/java/com/ignis/prestamil/request/PlazoHechuraAlhajaRequest.java,
    prestamil-backend/src/main/java/com/ignis/prestamil/response/PlazoHechuraAlhajaResponse.java,
    prestamil-backend/src/main/java/com/ignis/prestamil/mapper/PlazoHechuraAlhajaMapper.java
  </files>
  <action>
    **3.a — PlazoHechuraAlhajaRepository.java:**
    ```java
    package com.ignis.prestamil.repository;

    import com.ignis.prestamil.model.PlazoHechuraAlhaja;
    import com.ignis.prestamil.model.PlazoHechuraAlhajaId;
    import java.util.List;

    public interface PlazoHechuraAlhajaRepository
            extends BaseRepository<PlazoHechuraAlhaja, PlazoHechuraAlhajaId> {

        List<PlazoHechuraAlhaja> findByIdIdPlazoAndIdSucursalId(Integer idPlazo, Integer sucursalId);
    }
    ```
    (El doble `Id` en `findByIdIdPlazoAndIdSucursalId` es correcto: Spring Data interpreta `Id.idPlazo` → `getId().getIdPlazo()`.)

    **3.b — Modificar PlazoParametroRepository.java:**
    Agregar 2 métodos:
    ```java
    List<PlazoParametro> findByIdPlazoIdAndIdSucursalId(Long plazoId, Integer sucursalId);
    Optional<PlazoParametro> findByIdPlazoIdAndIdTipoPrendaIdAndIdSucursalId(
        Long plazoId, Integer tipoPrendaId, Integer sucursalId);
    ```
    Mantener los métodos existentes — solo AGREGAR. NO eliminar `findByPlazoId`, `findByTipoPrendaId`, etc.

    **3.c — PlazoHechuraAlhajaRequest.java:**
    ```java
    package com.ignis.prestamil.request;

    import jakarta.validation.constraints.*;
    import lombok.Getter;
    import lombok.Setter;
    import java.math.BigDecimal;

    @Getter
    @Setter
    public class PlazoHechuraAlhajaRequest {
        @NotNull(message = "kilataje es requerido")
        private Integer kilataje;

        @NotBlank(message = "hechura es requerida")
        @Pattern(regexp = "[FNE]", message = "hechura debe ser F, N o E")
        private String hechura;

        @NotNull(message = "precioBase es requerido")
        @DecimalMin(value = "0.0", inclusive = true)
        private BigDecimal precioBase;

        @NotNull(message = "porcAumento es requerido")
        @DecimalMin(value = "0.0", inclusive = true)
        private BigDecimal porcAumento;
    }
    ```

    **3.d — PlazoHechuraAlhajaResponse.java:**
    ```java
    package com.ignis.prestamil.response;

    import lombok.Getter;
    import lombok.Setter;
    import java.math.BigDecimal;

    @Getter
    @Setter
    public class PlazoHechuraAlhajaResponse {
        private Integer idPlazo;
        private Integer sucursalId;
        private Integer kilataje;
        private String hechura;
        private String hechuraDescripcion; // "Fina" | "Normal" | "Especial"
        private BigDecimal precioBase;
        private BigDecimal porcAumento;
        private BigDecimal precioPrestamo;
    }
    ```

    **3.e — PlazoHechuraAlhajaMapper.java (MANUAL, @Component — patrón del proyecto):**
    ```java
    package com.ignis.prestamil.mapper;

    import com.ignis.prestamil.model.PlazoHechuraAlhaja;
    import com.ignis.prestamil.model.PlazoHechuraAlhajaId;
    import com.ignis.prestamil.request.PlazoHechuraAlhajaRequest;
    import com.ignis.prestamil.response.PlazoHechuraAlhajaResponse;
    import org.springframework.stereotype.Component;

    @Component
    public class PlazoHechuraAlhajaMapper {

        public PlazoHechuraAlhajaResponse toResponse(PlazoHechuraAlhaja entity) {
            if (entity == null) return null;
            PlazoHechuraAlhajaResponse r = new PlazoHechuraAlhajaResponse();
            r.setIdPlazo(entity.getId().getIdPlazo());
            r.setSucursalId(entity.getId().getSucursalId());
            r.setKilataje(entity.getId().getKilataje());
            r.setHechura(entity.getId().getHechura());
            r.setHechuraDescripcion(describirHechura(entity.getId().getHechura()));
            r.setPrecioBase(entity.getPrecioBase());
            r.setPorcAumento(entity.getPorcAumento());
            r.setPrecioPrestamo(entity.getPrecioPrestamo());
            return r;
        }

        public PlazoHechuraAlhaja toEntity(PlazoHechuraAlhajaRequest req, Integer idPlazo, Integer sucursalId) {
            PlazoHechuraAlhaja e = new PlazoHechuraAlhaja();
            PlazoHechuraAlhajaId id = new PlazoHechuraAlhajaId(idPlazo, sucursalId, req.getKilataje(), req.getHechura());
            e.setId(id);
            e.setPrecioBase(req.getPrecioBase());
            e.setPorcAumento(req.getPorcAumento());
            return e;
        }

        private String describirHechura(String h) {
            return switch (h) {
                case "F" -> "Fina";
                case "N" -> "Normal";
                case "E" -> "Especial";
                default -> h;
            };
        }
    }
    ```
  </action>
  <verify>
    <automated>cd prestamil-backend &amp;&amp; ./mvnw.cmd compile -q 2>&amp;1 | tail -30</automated>
  </verify>
  <done>
    - PlazoHechuraAlhajaRepository creado y compila
    - PlazoParametroRepository tiene 2 métodos nuevos (con sucursalId)
    - PlazoHechuraAlhajaRequest tiene validaciones Bean Validation
    - PlazoHechuraAlhajaResponse incluye hechuraDescripcion
    - PlazoHechuraAlhajaMapper es @Component (NO @Mapper)
    - mvn compile pasa
  </done>
</task>

<!-- ============================================ -->
<!-- WAVE 4: Service + Controller (bug fix + nuevos métodos) -->
<!-- ============================================ -->

<task type="auto">
  <name>Task 4: Refactorizar PlazoService — bug fix + métodos para parámetros y alhajas con multi-sucursal</name>
  <files>
    prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java
  </files>
  <action>
    Modificar `PlazoService.java` para:

    **4.a — BUG FIX: Cambiar firma de getParametrosPlazo() para incluir sucursalId y lanzar excepción:**
    ```java
    /**
     * Obtiene los parámetros de préstamo para un plazo, tipo de prenda y sucursal.
     * @param idPlazo identificador del plazo
     * @param idTipoPrenda identificador del tipo de prenda
     * @param sucursalId identificador de la sucursal
     * @return PlazoParametroResponse con los parámetros configurados
     * @throws ResourceNotFoundException si no existe configuración para la combinación
     */
    public PlazoParametroResponse getParametrosPlazo(Long idPlazo, Integer idTipoPrenda, Integer sucursalId) {
        PlazoParametroId id = new PlazoParametroId(idPlazo, idTipoPrenda, sucursalId);
        return plazoParametroRepository.findById(id)
            .map(plazoParametroMapper::toPlazoParametroResponse)
            .orElseThrow(() -> new ResourceNotFoundException(
                "PlazoParametro", "plazo=" + idPlazo + ",tipoPrenda=" + idTipoPrenda + ",sucursal=" + sucursalId));
    }
    ```

    **4.b — Inyectar PlazoHechuraAlhajaRepository y PlazoHechuraAlhajaMapper** vía `@RequiredArgsConstructor` (agregar campos `final`).

    **4.c — Nuevos métodos para parámetros multi-sucursal:**
    ```java
    /**
     * Lista todos los parámetros de préstamo configurados para una sucursal de un plazo.
     */
    public List<PlazoParametroResponse> getParametrosBySucursal(Long plazoId, Integer sucursalId) {
        return plazoParametroRepository.findByIdPlazoIdAndIdSucursalId(plazoId, sucursalId)
            .stream()
            .map(plazoParametroMapper::toPlazoParametroResponse)
            .toList();
    }

    /**
     * Crea o actualiza (upsert) los parámetros para una combinación plazo+tipoPrenda+sucursal.
     */
    public PlazoParametroResponse guardarParametro(Long plazoId, Integer tipoPrendaId,
                                                    Integer sucursalId, PlazoParametroRequest request) {
        PlazoParametroId id = new PlazoParametroId(plazoId, tipoPrendaId, sucursalId);
        PlazoParametro entity = plazoParametroRepository.findById(id)
            .orElseGet(() -> {
                PlazoParametro nuevo = plazoParametroMapper.toPlazoParametro(request);
                nuevo.setId(id);
                return nuevo;
            });
        // Mapeo de campos editables desde el request (todos menos id)
        plazoParametroMapper.actualizarDesdeRequest(entity, request);
        PlazoParametro guardado = plazoParametroRepository.save(entity);
        return plazoParametroMapper.toPlazoParametroResponse(guardado);
    }
    ```

    NOTA: Si `PlazoParametroMapper` no tiene `actualizarDesdeRequest()`, agregarlo (es mapper manual, basta con setters explícitos campo por campo).

    **4.d — Nuevos métodos para tabla de alhajas:**
    ```java
    private static final BigDecimal FACTOR_TROY_ONZA = new BigDecimal("31.1035");
    private static final BigDecimal KILATES_PUROS = new BigDecimal("24");

    /**
     * Lista la tabla de hechuras de alhajas para un plazo+sucursal.
     */
    public List<PlazoHechuraAlhajaResponse> getTablaAlhajas(Integer idPlazo, Integer sucursalId) {
        return plazoHechuraAlhajaRepository.findByIdIdPlazoAndIdSucursalId(idPlazo, sucursalId)
            .stream()
            .map(plazoHechuraAlhajaMapper::toResponse)
            .toList();
    }

    /**
     * Actualiza el precio base de una hechura específica y recalcula el precio de préstamo:
     *   precioPrestamo = precioBase * (1 + porcAumento)
     */
    public PlazoHechuraAlhajaResponse actualizarPrecioBase(Integer idPlazo, Integer sucursalId,
                                                            Integer kilataje, String hechura,
                                                            BigDecimal precioBase) {
        PlazoHechuraAlhajaId id = new PlazoHechuraAlhajaId(idPlazo, sucursalId, kilataje, hechura);
        PlazoHechuraAlhaja entity = plazoHechuraAlhajaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("PlazoHechuraAlhaja", id.toString()));
        entity.setPrecioBase(precioBase);
        BigDecimal precioPrestamo = precioBase.multiply(BigDecimal.ONE.add(entity.getPorcAumento()))
            .setScale(4, java.math.RoundingMode.HALF_UP);
        entity.setPrecioPrestamo(precioPrestamo);
        return plazoHechuraAlhajaMapper.toResponse(plazoHechuraAlhajaRepository.save(entity));
    }

    /**
     * Recalcula precioBase y precioPrestamo para TODOS los registros del plazo+sucursal usando
     * un precio base de oro de 24 kilates por onza troy.
     *   precioBaseKilate = (precioBaseOro / 24) * kilataje * 31.1035
     *   precioPrestamo   = precioBaseKilate * (1 + porcAumento)
     * Esta operación afecta a todos los kilatajes y hechuras del plazo+sucursal.
     */
    public void actualizarTodosPrecios(Integer idPlazo, Integer sucursalId, BigDecimal precioBaseOro) {
        List<PlazoHechuraAlhaja> registros =
            plazoHechuraAlhajaRepository.findByIdIdPlazoAndIdSucursalId(idPlazo, sucursalId);
        if (registros.isEmpty()) {
            throw new ResourceNotFoundException("PlazoHechuraAlhaja",
                "No hay registros para plazo=" + idPlazo + ", sucursal=" + sucursalId);
        }
        BigDecimal precioPorKilatePuro = precioBaseOro.divide(KILATES_PUROS, 10, java.math.RoundingMode.HALF_UP);
        for (PlazoHechuraAlhaja r : registros) {
            BigDecimal precioBase = precioPorKilatePuro
                .multiply(new BigDecimal(r.getId().getKilataje()))
                .multiply(FACTOR_TROY_ONZA)
                .setScale(4, java.math.RoundingMode.HALF_UP);
            r.setPrecioBase(precioBase);
            r.setPrecioPrestamo(precioBase.multiply(BigDecimal.ONE.add(r.getPorcAumento()))
                .setScale(4, java.math.RoundingMode.HALF_UP));
        }
        plazoHechuraAlhajaRepository.saveAll(registros);
    }
    ```

    **4.e — Imports adicionales:** `BigDecimal`, `RoundingMode`, los nuevos repositorios/mappers/DTOs.

    NOTA: La firma de `getParametrosPlazo` cambia — esto romperá la llamada actual en `PlazoController`. Esto se arreglará en la Task 5 (controller). Documentar con `@Deprecated` NO; simplemente refactorizar el caller en el controller.
  </action>
  <verify>
    <automated>cd prestamil-backend &amp;&amp; ./mvnw.cmd compile -q 2>&amp;1 | tail -40</automated>
  </verify>
  <done>
    - getParametrosPlazo() ya no devuelve null — lanza ResourceNotFoundException
    - Nuevos métodos: getParametrosBySucursal, guardarParametro, getTablaAlhajas, actualizarPrecioBase, actualizarTodosPrecios
    - Fórmula del oro usa FACTOR_TROY_ONZA = 31.1035 y KILATES_PUROS = 24
    - PlazoHechuraAlhajaRepository y Mapper inyectados via constructor
    - Compila sin errores (puede haber warning si controller ya no compila — se arregla en Task 5)
  </done>
</task>

<task type="auto">
  <name>Task 5: Agregar 5 endpoints nuevos en PlazoController + actualizar firma del endpoint existente</name>
  <files>
    prestamil-backend/src/main/java/com/ignis/prestamil/controller/PlazoController.java
  </files>
  <action>
    Modificar `PlazoController.java` para:

    **5.a — Actualizar endpoint existente** `GET /api/plazos/{id}/parametros/{idTipoPrenda}` para aceptar `sucursalId` como `@RequestParam` con default=1:
    ```java
    @GetMapping("/{id}/parametros/{idTipoPrenda}")
    public ResponseEntity<PlazoParametroResponse> getParametrosPlazo(
            @PathVariable Long id,
            @PathVariable Integer idTipoPrenda,
            @RequestParam(defaultValue = "1") Integer sucursalId) {
        return ResponseEntity.ok(plazoService.getParametrosPlazo(id, idTipoPrenda, sucursalId));
    }
    ```

    **5.b — Agregar 5 endpoints nuevos:**
    ```java
    // Lista parámetros de una sucursal
    @GetMapping("/{id}/parametros")
    public ResponseEntity<List<PlazoParametroResponse>> getParametrosBySucursal(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer sucursalId) {
        return ResponseEntity.ok(plazoService.getParametrosBySucursal(id, sucursalId));
    }

    // Upsert de parámetros para una combinación
    @PutMapping("/{id}/parametros/{tipoPrendaId}")
    public ResponseEntity<PlazoParametroResponse> guardarParametro(
            @PathVariable Long id,
            @PathVariable Integer tipoPrendaId,
            @RequestParam(defaultValue = "1") Integer sucursalId,
            @Valid @RequestBody PlazoParametroRequest request) {
        return ResponseEntity.ok(plazoService.guardarParametro(id, tipoPrendaId, sucursalId, request));
    }

    // Tabla de alhajas
    @GetMapping("/{id}/alhajas")
    public ResponseEntity<List<PlazoHechuraAlhajaResponse>> getTablaAlhajas(
            @PathVariable Integer id,
            @RequestParam(defaultValue = "1") Integer sucursalId) {
        return ResponseEntity.ok(plazoService.getTablaAlhajas(id, sucursalId));
    }

    // Actualizar precio base de un kilataje+hechura
    @PutMapping("/{id}/alhajas/{kilataje}/{hechura}")
    public ResponseEntity<PlazoHechuraAlhajaResponse> actualizarPrecioBase(
            @PathVariable Integer id,
            @PathVariable Integer kilataje,
            @PathVariable String hechura,
            @RequestParam(defaultValue = "1") Integer sucursalId,
            @RequestBody Map<String, BigDecimal> body) {
        BigDecimal precioBase = body.get("precioBase");
        if (precioBase == null) {
            throw new BadRequestException("precioBase es requerido en el body");
        }
        return ResponseEntity.ok(
            plazoService.actualizarPrecioBase(id, sucursalId, kilataje, hechura, precioBase));
    }

    // Recalcular todos los precios usando precio base de oro
    @PutMapping("/{id}/alhajas/precio-oro")
    public ResponseEntity<List<PlazoHechuraAlhajaResponse>> actualizarTodosPrecios(
            @PathVariable Integer id,
            @RequestParam(defaultValue = "1") Integer sucursalId,
            @RequestBody Map<String, BigDecimal> body) {
        BigDecimal precioBaseOro = body.get("precioBaseOro");
        if (precioBaseOro == null) {
            throw new BadRequestException("precioBaseOro es requerido en el body");
        }
        plazoService.actualizarTodosPrecios(id, sucursalId, precioBaseOro);
        return ResponseEntity.ok(plazoService.getTablaAlhajas(id, sucursalId));
    }
    ```

    **5.c — Imports adicionales:** `java.math.BigDecimal`, `java.util.Map`, `BadRequestException`, `PlazoHechuraAlhajaResponse`, `jakarta.validation.Valid`.
  </action>
  <verify>
    <automated>cd prestamil-backend &amp;&amp; ./mvnw.cmd compile -q 2>&amp;1 | tail -30</automated>
  </verify>
  <done>
    - 5 endpoints nuevos registrados con `@RequestParam(defaultValue="1") Integer sucursalId`
    - Endpoint existente `/parametros/{idTipoPrenda}` ahora acepta sucursalId
    - `@Valid` agregado al request body de guardarParametro
    - mvn compile pasa
  </done>
</task>

<!-- ============================================ -->
<!-- WAVE 5: Validación backend completa          -->
<!-- ============================================ -->

<task type="auto">
  <name>Task 6: Validar compilación backend completa + tests existentes</name>
  <files>
    (ningún archivo nuevo — sólo validación)
  </files>
  <action>
    Ejecutar build completo del backend para asegurar:
    1. `mvn clean compile` sin errores
    2. `mvn test` — todos los tests existentes pasan (26 tests de la fase 260515-0is)
    3. La inyección de los nuevos beans (PlazoHechuraAlhajaRepository, PlazoHechuraAlhajaMapper) en PlazoService no rompe el contexto de Spring

    Si algún test falla por la firma cambiada de `getParametrosPlazo(Long, Integer)` → `getParametrosPlazo(Long, Integer, Integer)`, actualizar el test correspondiente (likely en `PlazoServiceTest` si existe) para pasar el tercer parámetro sucursalId=1.

    Reportar:
    - Resultado de mvn compile (BUILD SUCCESS o errores)
    - Resultado de mvn test (X tests, Y failures)
    - Lista de archivos modificados (debe coincidir con files_modified)
  </action>
  <verify>
    <automated>cd prestamil-backend &amp;&amp; ./mvnw.cmd clean compile test -q 2>&amp;1 | tail -50</automated>
  </verify>
  <done>
    - mvn clean compile = BUILD SUCCESS
    - mvn test = todos los tests pasan (o ajustados para nueva firma)
    - Sin warnings de Hibernate sobre mapping inválido
    - Contexto Spring carga correctamente
  </done>
</task>

<!-- ============================================ -->
<!-- WAVE 6: Frontend — modelos y servicio (independientes del componente) -->
<!-- ============================================ -->

<task type="auto">
  <name>Task 7: Crear plazo.model.ts y plazo.service.ts en frontend</name>
  <files>
    prestamil-frontend/src/app/prestamil/core/models/plazo.model.ts,
    prestamil-frontend/src/app/prestamil/core/services/plazo.service.ts
  </files>
  <action>
    **7.a — plazo.model.ts:**
    Definir interfaces TypeScript que reflejen los DTOs backend (sin prefijo `I`):

    ```typescript
    // Requests
    export interface PlazoRequest {
      nombre: string;
      diasPorPeriodo: number;
      numeroPeriodos: number;
      activo: boolean;
      tiposPrenda: number[];
    }

    export interface PlazoParametroRequest {
      // todos los campos editables del request actual (revisar PlazoParametroRequest.java backend)
      porcInteres: number;
      porcAlmacen: number;
      // ... (los demás según el request real — copiarlos uno a uno)
    }

    export interface PlazoHechuraAlhajaRequest {
      kilataje: number;
      hechura: 'F' | 'N' | 'E';
      precioBase: number;
      porcAumento: number;
    }

    // Responses
    export interface TipoPrendaResponse { id: number; descripcion: string; }

    export interface PlazoResponse {
      id: number;
      nombre: string;
      diasPorPeriodo: number;
      numeroPeriodos: number;
      activo: boolean;
      tiposPrenda?: TipoPrendaResponse[];
    }

    export interface PlazoParametroResponse {
      plazoId: number;
      tipoPrendaId: number;
      sucursalId: number;
      tipoPrenda?: TipoPrendaResponse;
      porcInteres: number;
      porcAlmacen: number;
      // ... resto de campos
    }

    export interface PlazoHechuraAlhajaResponse {
      idPlazo: number;
      sucursalId: number;
      kilataje: number;
      hechura: string;
      hechuraDescripcion: string;
      precioBase: number;
      porcAumento: number;
      precioPrestamo: number;
    }
    ```

    IMPORTANTE: Al copiar los campos de PlazoParametroResponse, revisar el archivo Java real para listar TODOS los campos. Mantener camelCase.

    **7.b — plazo.service.ts:**
    ```typescript
    import { HttpClient } from '@angular/common/http';
    import { Injectable, inject } from '@angular/core';
    import { Observable } from 'rxjs';
    import { environment } from '../../../../environments/environment';
    import {
      PlazoRequest, PlazoResponse,
      PlazoParametroRequest, PlazoParametroResponse,
      PlazoHechuraAlhajaResponse
    } from '../models/plazo.model';

    @Injectable({ providedIn: 'root' })
    export class PlazoService {
      private readonly http = inject(HttpClient);
      private readonly API_URL = `${environment.apiUrl}/api/plazos`;

      // === Plazos CRUD ===
      getAll(): Observable<PlazoResponse[]> {
        return this.http.get<PlazoResponse[]>(this.API_URL);
      }
      getById(id: number): Observable<PlazoResponse> {
        return this.http.get<PlazoResponse>(`${this.API_URL}/${id}`);
      }
      create(request: PlazoRequest): Observable<PlazoResponse> {
        return this.http.post<PlazoResponse>(this.API_URL, request);
      }
      update(id: number, request: PlazoRequest): Observable<PlazoResponse> {
        return this.http.put<PlazoResponse>(`${this.API_URL}/${id}`, request);
      }

      // === Parámetros multi-sucursal ===
      getParametrosBySucursal(plazoId: number, sucursalId: number = 1): Observable<PlazoParametroResponse[]> {
        return this.http.get<PlazoParametroResponse[]>(
          `${this.API_URL}/${plazoId}/parametros`, { params: { sucursalId } });
      }
      getParametro(plazoId: number, tipoPrendaId: number, sucursalId: number = 1): Observable<PlazoParametroResponse> {
        return this.http.get<PlazoParametroResponse>(
          `${this.API_URL}/${plazoId}/parametros/${tipoPrendaId}`, { params: { sucursalId } });
      }
      guardarParametro(plazoId: number, tipoPrendaId: number, request: PlazoParametroRequest, sucursalId: number = 1): Observable<PlazoParametroResponse> {
        return this.http.put<PlazoParametroResponse>(
          `${this.API_URL}/${plazoId}/parametros/${tipoPrendaId}`, request, { params: { sucursalId } });
      }

      // === Tabla alhajas ===
      getTablaAlhajas(plazoId: number, sucursalId: number = 1): Observable<PlazoHechuraAlhajaResponse[]> {
        return this.http.get<PlazoHechuraAlhajaResponse[]>(
          `${this.API_URL}/${plazoId}/alhajas`, { params: { sucursalId } });
      }
      actualizarPrecioBase(plazoId: number, kilataje: number, hechura: string, precioBase: number, sucursalId: number = 1): Observable<PlazoHechuraAlhajaResponse> {
        return this.http.put<PlazoHechuraAlhajaResponse>(
          `${this.API_URL}/${plazoId}/alhajas/${kilataje}/${hechura}`,
          { precioBase }, { params: { sucursalId } });
      }
      actualizarTodosPrecios(plazoId: number, precioBaseOro: number, sucursalId: number = 1): Observable<PlazoHechuraAlhajaResponse[]> {
        return this.http.put<PlazoHechuraAlhajaResponse[]>(
          `${this.API_URL}/${plazoId}/alhajas/precio-oro`,
          { precioBaseOro }, { params: { sucursalId } });
      }
    }
    ```

    Convenciones del proyecto:
    - `inject()` style (no constructor injection)
    - private readonly constants en SCREAMING_SNAKE_CASE
    - sucursalId default=1 alineado con backend
  </action>
  <verify>
    <automated>cd prestamil-frontend &amp;&amp; npx ng build --configuration=development 2>&amp;1 | tail -30</automated>
  </verify>
  <done>
    - plazo.model.ts exporta todas las interfaces necesarias
    - plazo.service.ts inyecta HttpClient via inject()
    - 9 métodos definidos cubriendo todos los endpoints
    - ng build pasa sin errores de tipos
  </done>
</task>

<!-- ============================================ -->
<!-- WAVE 7: Frontend — componentes UI            -->
<!-- ============================================ -->

<task type="auto">
  <name>Task 8: Refactorizar plazos-periodos.component a layout dos paneles con tabs + implementar parametros-prestamo.component</name>
  <files>
    prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.ts,
    prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.html,
    prestamil-frontend/src/app/prestamil/pages/configuracion/parametros-prestamo/parametros-prestamo.component.ts,
    prestamil-frontend/src/app/prestamil/pages/configuracion/parametros-prestamo/parametros-prestamo.component.html
  </files>
  <action>
    **8.a — Refactor plazos-periodos.component.ts:**

    Layout objetivo (dos paneles + tabs):
    ```
    +-------------------+--------------------------------------------------+
    |  Panel izquierdo  |  Panel derecho (detalle del plazo seleccionado) |
    |  - Lista plazos   |  ┌─Tabs──────────────────────────────────┐      |
    |  - Btn nuevo      |  │ Parámetros │ Alhajas                  │      |
    |  - Btn editar     |  ├──────────────────────────────────────┤      |
    |  - Btn activar    |  │ [contenido del tab activo]           │      |
    |                   |  └──────────────────────────────────────┘      |
    +-------------------+--------------------------------------------------+
    ```

    Mantener funcionalidad existente: CRUD de plazos (lista, nuevo, editar, activar/desactivar) — solo reorganizar a panel izquierdo. Modal de edición de plazo se conserva.

    Agregar al componente:
    - `selectedPlazo: PlazoResponse | null = null` — plazo seleccionado en panel izquierdo
    - `activeTab: 'parametros' | 'alhajas' = 'parametros'`
    - `parametros: PlazoParametroResponse[] = []`
    - `alhajas: PlazoHechuraAlhajaResponse[] = []`
    - `sucursalId = 1` (hardcoded por ahora, futuro: del estado de sesión)
    - `precioBaseOroInput: number | null = null` — campo para recálculo masivo

    Métodos:
    - `seleccionarPlazo(plazo: PlazoResponse)`: setea selectedPlazo, carga datos del tab activo
    - `cambiarTab(tab: 'parametros' | 'alhajas')`: setea activeTab, carga datos del tab
    - `cargarParametros()`: llama `plazoService.getParametrosBySucursal(selectedPlazo.id, sucursalId)`
    - `cargarAlhajas()`: llama `plazoService.getTablaAlhajas(selectedPlazo.id, sucursalId)`
    - `actualizarPrecioBase(alhaja: PlazoHechuraAlhajaResponse, nuevoPrecio: number)`: llama `actualizarPrecioBase()` y recarga la fila
    - `recalcularTodo()`: si `precioBaseOroInput > 0`, llama `actualizarTodosPrecios()` y recarga toda la tabla

    Convenciones:
    - inject(PlazoService) — NO constructor
    - Patrón `successMessage` / `errorMessage` para feedback
    - `isLoadingData` mientras se cargan tabs

    **8.b — plazos-periodos.component.html:**
    - Estructura row con 2 columnas (`col-md-4` izquierda, `col-md-8` derecha)
    - Panel izquierdo: tabla/lista de plazos con click handler
    - Panel derecho:
      - Si `!selectedPlazo`: mensaje "Seleccione un plazo"
      - Si `selectedPlazo`: ngb-nav con 2 tabs
        - Tab "Parámetros": tabla read-only con columnas Tipo Prenda | % Interés | % Almacén | etc.
        - Tab "Alhajas":
          - Input numérico "Precio base oro (24K)" + botón "Recalcular todos"
          - Tabla con columnas Kilataje | Hechura | Precio Base | % Aumento | Precio Préstamo | [Editar]
          - Cada fila editable inline para precioBase (sin modal — actualización directa al blur o botón)

    Usar ng-bootstrap NgbNav para los tabs (ya disponible vía SharedModule).

    **8.c — parametros-prestamo.component.ts (NUEVO, vista solo lectura):**
    ```typescript
    import { Component, OnInit, inject } from '@angular/core';
    import { CommonModule } from '@angular/common';
    import { SharedModule } from '../../../../theme/shared/shared.module';
    import { PlazoService } from '../../../core/services/plazo.service';
    import { PlazoResponse, PlazoParametroResponse } from '../../../core/models/plazo.model';
    import { forkJoin } from 'rxjs';

    @Component({
      selector: 'app-parametros-prestamo',
      standalone: true,
      imports: [CommonModule, SharedModule],
      templateUrl: './parametros-prestamo.component.html'
    })
    export class ParametrosPrestamoComponent implements OnInit {
      private readonly plazoService = inject(PlazoService);

      plazos: PlazoResponse[] = [];
      parametrosPorPlazo: { plazo: PlazoResponse; parametros: PlazoParametroResponse[] }[] = [];
      sucursalId = 1;
      isLoading = false;
      errorMessage = '';

      ngOnInit(): void {
        this.cargarTodo();
      }

      cargarTodo(): void {
        this.isLoading = true;
        this.plazoService.getAll().subscribe({
          next: (plazos) => {
            this.plazos = plazos.filter(p => p.activo);
            if (this.plazos.length === 0) {
              this.isLoading = false;
              return;
            }
            const calls = this.plazos.map(p =>
              this.plazoService.getParametrosBySucursal(p.id, this.sucursalId));
            forkJoin(calls).subscribe({
              next: (results) => {
                this.parametrosPorPlazo = this.plazos.map((p, i) => ({
                  plazo: p,
                  parametros: results[i]
                }));
                this.isLoading = false;
              },
              error: (err) => {
                this.errorMessage = 'Error al cargar parámetros: ' + (err?.error?.message ?? err.message);
                this.isLoading = false;
              }
            });
          },
          error: (err) => {
            this.errorMessage = 'Error al cargar plazos: ' + (err?.error?.message ?? err.message);
            this.isLoading = false;
          }
        });
      }
    }
    ```

    **8.d — parametros-prestamo.component.html:**
    - Loop por `parametrosPorPlazo`:
      - Card con título = `{{ item.plazo.nombre }}` ({{ item.plazo.diasPorPeriodo }} días x {{ item.plazo.numeroPeriodos }})
      - Tabla con columnas: Tipo Prenda | % Interés | % Almacén | (resto de columnas según campos del response)
      - Si `item.parametros.length === 0`: mensaje "Sin parámetros configurados para esta sucursal"
    - Indicador `<app-spinner>` o equivalente cuando `isLoading`
    - Banner de error si `errorMessage`

    NOTAS:
    - El componente es SOLO LECTURA — sin formularios, sin botones de editar, sin modales
    - Usar la convención de `inject()` style (NO constructor)
    - Standalone component (compatible con la ruta lazy-loaded existente)
  </action>
  <verify>
    <automated>cd prestamil-frontend &amp;&amp; npx ng build --configuration=development 2>&amp;1 | tail -30</automated>
  </verify>
  <done>
    - plazos-periodos.component muestra dos paneles + tabs funcionales
    - Tab Parámetros lista parámetros por tipo de prenda
    - Tab Alhajas permite editar precio base y recalcular masivo
    - parametros-prestamo.component implementado como vista solo lectura agrupada por plazo
    - ng build pasa sin errores
    - Ambos componentes usan inject() y standalone
  </done>
</task>

<!-- ============================================ -->
<!-- WAVE 8: Documentación                        -->
<!-- ============================================ -->

<task type="auto">
  <name>Task 9: Actualizar ARCHITECTURE.md, CONCERNS.md y STRUCTURE.md con cambios del módulo</name>
  <files>
    .planning/codebase/ARCHITECTURE.md,
    .planning/codebase/CONCERNS.md,
    .planning/codebase/STRUCTURE.md
  </files>
  <action>
    **9.a — ARCHITECTURE.md:**
    En la tabla "API Route Table" (después de las filas existentes de `/api/plazos`), agregar:
    ```
    | GET | `/api/plazos/{id}/parametros` | `PlazoController` | List params por sucursal (?sucursalId=1) |
    | PUT | `/api/plazos/{id}/parametros/{tipoPrendaId}` | `PlazoController` | Upsert param (?sucursalId=1) |
    | GET | `/api/plazos/{id}/alhajas` | `PlazoController` | Tabla hechuras por sucursal (?sucursalId=1) |
    | PUT | `/api/plazos/{id}/alhajas/{kilataje}/{hechura}` | `PlazoController` | Actualizar precio base de una fila |
    | PUT | `/api/plazos/{id}/alhajas/precio-oro` | `PlazoController` | Recalcular todos usando precioBaseOro |
    ```

    Actualizar la fila existente:
    `| GET | /api/plazos/{id}/parametros/{idTipo} | PlazoController | Term params (?sucursalId=1) — throws 404 if not found |`

    En "Module Breakdown" actualizar la fila de Plazos:
    `| Plazos | PlazoController, PlazoService, PlazoRepository, PlazoParametroRepository, PlazoHechuraAlhajaRepository, PlazoMapper, PlazoParametroMapper, PlazoHechuraAlhajaMapper | PlazosPeriodosComponent, ParametrosPrestamoComponent, PlazoService (TS) |`

    **9.b — CONCERNS.md:**
    En la sección "Missing Error Handling", la entrada de `PlazoService.getParametrosPlazo()` debe marcarse como RESUELTO:
    ```
    **~~PlazoService.getParametrosPlazo() returns null instead of throwing:~~** RESOLVED 2026-05-16 (quick task 260516-mns) — ahora lanza ResourceNotFoundException y acepta sucursalId como tercer parámetro.
    ```

    En "Incomplete Features / TODOs", marcar la entrada de `ParametrosPrestamoComponent` como RESUELTO:
    ```
    **~~Parametros Préstamo component is an empty shell:~~** RESOLVED 2026-05-16 (quick task 260516-mns) — implementado como vista solo lectura que muestra parámetros agrupados por plazo desde el backend.
    ```

    **9.c — STRUCTURE.md:**
    En la sección Backend, bajo `model/`, agregar (en orden alfabético):
    ```
    ├── PlazoHechuraAlhaja.java          # Tabla de hechuras de oro por plazo, sucursal, kilataje y hechura
    ├── PlazoHechuraAlhajaId.java        # Clave compuesta (idPlazo, sucursalId, kilataje, hechura)
    ```

    Bajo `repository/`:
    ```
    ├── PlazoHechuraAlhajaRepository.java
    ```

    Bajo `request/`:
    ```
    ├── PlazoHechuraAlhajaRequest.java
    ```

    Bajo `response/`:
    ```
    ├── PlazoHechuraAlhajaResponse.java
    ```

    Bajo `mapper/`:
    ```
    ├── PlazoHechuraAlhajaMapper.java
    ```

    En la sección Frontend, bajo `core/models/`:
    ```
    ├── plazo.model.ts                  # PlazoRequest/Response, PlazoParametro*, PlazoHechuraAlhaja* interfaces
    ```

    Bajo `core/services/`:
    ```
    ├── plazo.service.ts                # CRUD plazos + parámetros multi-sucursal + tabla alhajas
    ```

    En la sección "Where to Add New Code" — sin cambios.

    Si hay sección `db/changelog/` documentada, agregar el archivo `006-plazos-sucursal.sql`. Si no, omitir.
  </action>
  <verify>
    <automated>grep -c "PlazoHechuraAlhaja" .planning/codebase/ARCHITECTURE.md .planning/codebase/STRUCTURE.md</automated>
  </verify>
  <done>
    - ARCHITECTURE.md tabla de API incluye los 5 endpoints nuevos
    - ARCHITECTURE.md Module Breakdown lista PlazoHechuraAlhaja* + ParametrosPrestamoComponent
    - CONCERNS.md marca como RESUELTO el bug getParametrosPlazo y ParametrosPrestamoComponent vacío
    - STRUCTURE.md lista los 5 archivos nuevos del backend y 2 del frontend
  </done>
</task>

</tasks>

<verification>
**Verificaciones de fase completa (ejecutar al finalizar todos los tasks):**

1. **Backend compila y tests pasan:**
   ```bash
   cd prestamil-backend && ./mvnw.cmd clean compile test
   ```
   Esperado: BUILD SUCCESS, todos los tests pasan.

2. **Frontend compila:**
   ```bash
   cd prestamil-frontend && npx ng build --configuration=development
   ```
   Esperado: Build sin errores de tipos, sin warnings nuevos.

3. **Estructura de archivos final (todos los archivos nuevos existen):**
   - `prestamil-backend/src/main/resources/db/changelog/changes/006-plazos-sucursal.sql`
   - `prestamil-backend/src/main/java/com/ignis/prestamil/model/PlazoHechuraAlhaja.java`
   - `prestamil-backend/src/main/java/com/ignis/prestamil/model/PlazoHechuraAlhajaId.java`
   - `prestamil-backend/src/main/java/com/ignis/prestamil/repository/PlazoHechuraAlhajaRepository.java`
   - `prestamil-backend/src/main/java/com/ignis/prestamil/request/PlazoHechuraAlhajaRequest.java`
   - `prestamil-backend/src/main/java/com/ignis/prestamil/response/PlazoHechuraAlhajaResponse.java`
   - `prestamil-backend/src/main/java/com/ignis/prestamil/mapper/PlazoHechuraAlhajaMapper.java`
   - `prestamil-frontend/src/app/prestamil/core/models/plazo.model.ts`
   - `prestamil-frontend/src/app/prestamil/core/services/plazo.service.ts`
   - `prestamil-frontend/src/app/prestamil/pages/configuracion/parametros-prestamo/parametros-prestamo.component.html`

4. **Convenciones del proyecto respetadas:**
   - Entidades JPA usan `@Getter @Setter` (NO `@Data`)
   - Mappers son `@Component` manuales (NO `@Mapper(componentModel="spring")`)
   - Todos los `@Column` tienen `name=` explícito
   - DTOs request tienen anotaciones Bean Validation (`@NotNull`, `@NotBlank`)
   - Frontend usa `inject()` style (NO constructor injection)
   - Errores usan `ResourceNotFoundException` (404) y `BadRequestException` (400)

5. **Documentación actualizada:**
   - ARCHITECTURE.md menciona PlazoHechuraAlhaja en Module Breakdown
   - CONCERNS.md marca getParametrosPlazo y ParametrosPrestamoComponent como RESUELTOS
   - STRUCTURE.md lista los archivos nuevos
</verification>

<success_criteria>
**El plan se considera exitoso cuando:**

- [ ] Migración Liquibase 006 aplica sin errores y agrega `sucursal_id` a `plazo_parametro` y `plazo_hechura_alhaja`
- [ ] PlazoParametroId incluye sucursalId en clave compuesta (3 campos: plazoId, tipoPrendaId, sucursalId)
- [ ] PlazoHechuraAlhaja entity completo con @EmbeddedId (4 campos en la clave: idPlazo, sucursalId, kilataje, hechura)
- [ ] PlazoService.getParametrosPlazo() lanza ResourceNotFoundException en vez de null
- [ ] 5 nuevos endpoints REST funcionando: `/parametros` (GET sucursal, PUT upsert), `/alhajas` (GET tabla, PUT precio individual, PUT precio-oro masivo)
- [ ] Fórmula del oro implementada correctamente: `(precioBaseOro / 24) * kilataje * 31.1035`
- [ ] Frontend plazo.service.ts expone 9 métodos cubriendo todos los endpoints
- [ ] plazos-periodos.component muestra layout dos paneles con tabs Parámetros/Alhajas
- [ ] parametros-prestamo.component muestra vista solo lectura agrupada por plazo (ya no es un shell vacío)
- [ ] `mvn clean compile test` = BUILD SUCCESS
- [ ] `ng build` = compila sin errores
- [ ] ARCHITECTURE.md, CONCERNS.md y STRUCTURE.md actualizados
</success_criteria>

<output>
After completion, create `.planning/quick/260516-mns-implementar-m-dulo-plazohechuraalhaja-co/260516-mns-SUMMARY.md` documenting:
- What was built (backend entities, endpoints, frontend components)
- What changed (PlazoParametroId signature, getParametrosPlazo signature)
- How to verify locally (endpoints to test with curl/Postman)
- Known follow-ups (e.g., sucursalId hardcoded a 1 — futuro: leer de sesión)
</output>
