---
phase: quick-260516-oio
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.ts
  - prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.html
  - prestamil-frontend/src/app/prestamil/core/services/plazo.service.ts
  - prestamil-backend/src/main/java/com/ignis/prestamil/controller/PlazoController.java
  - prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java
autonomous: true
requirements:
  - QUICK-OIO-FIX1
  - QUICK-OIO-FIX2
  - QUICK-OIO-FIX3

must_haves:
  truths:
    - "El modal de Plazo muestra checkboxes con todos los TipoPrenda disponibles y permite seleccionarlos"
    - "La lista de plazos muestra chips con los TipoPrenda asociados debajo del nombre de cada plazo"
    - "El tab Parámetros muestra un formulario editable por cada tipo de prenda asociado al plazo, con botón Guardar individual"
    - "Guardar parámetros con upsert: si no existe el registro lo crea, si existe lo actualiza"
    - "Cuando alhajas está vacío, aparece botón 'Inicializar tabla estándar' que crea 12 combinaciones (10K/14K/18K/24K × F/N/E)"
    - "Existe formulario para agregar una alhaja individual (kilataje + hechura + precioBase + porcAumento)"
    - "El endpoint POST /api/plazos/{id}/alhajas crea una alhaja nueva o devuelve 400 si ya existe la combinación"
  artifacts:
    - path: "prestamil-backend/src/main/java/com/ignis/prestamil/controller/PlazoController.java"
      provides: "Endpoint POST /{id}/alhajas para crear alhaja"
      contains: "@PostMapping(\"/{id}/alhajas\")"
    - path: "prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java"
      provides: "Método crearAlhaja con validación de duplicados y cálculo de precioPrestamo"
      contains: "public PlazoHechuraAlhajaResponse crearAlhaja"
    - path: "prestamil-frontend/src/app/prestamil/core/services/plazo.service.ts"
      provides: "Método crearAlhaja(plazoId, request, sucursalId)"
      contains: "crearAlhaja("
    - path: "prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.ts"
      provides: "cargarTiposPrenda real + estado parametrosForm + guardarParametro + agregarAlhaja + inicializarTablaEstandar + nuevaAlhaja"
      contains: "guardarParametro"
    - path: "prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.html"
      provides: "Checkboxes de tipos de prenda en modal + chips en lista + formularios editables en tab Parámetros + form agregar alhaja + botón inicializar"
      contains: "inicializarTablaEstandar"
  key_links:
    - from: "plazos-periodos.component.ts (cargarTiposPrenda)"
      to: "GET /api/prendas/tipos"
      via: "HttpClient inyectado con inject()"
      pattern: "api/prendas/tipos"
    - from: "plazos-periodos.component.ts (guardarParametro)"
      to: "plazoService.guardarParametro"
      via: "PUT /api/plazos/{id}/parametros/{tipoPrendaId}"
      pattern: "plazoService\\.guardarParametro"
    - from: "plazos-periodos.component.ts (agregarAlhaja, inicializarTablaEstandar)"
      to: "plazoService.crearAlhaja"
      via: "POST /api/plazos/{id}/alhajas"
      pattern: "plazoService\\.crearAlhaja"
    - from: "PlazoController.crearAlhaja"
      to: "PlazoService.crearAlhaja"
      via: "Inyección Spring vía @RequiredArgsConstructor"
      pattern: "plazoService\\.crearAlhaja"
---

<objective>
Aplicar tres fixes en el módulo plazos-periodos:
1. Conectar `cargarTiposPrenda()` al endpoint real y agregar checkboxes en el modal + chips en la lista.
2. Convertir el tab Parámetros en formularios editables con upsert por tipo de prenda.
3. Permitir crear/inicializar registros en la tabla `plazo_hechura_alhaja` (1 endpoint backend nuevo + UI frontend).

Purpose: Completar la funcionalidad del módulo refactorizado en quick task 260516-mns que quedó con tipos de prenda no funcionales, parámetros read-only y sin forma de poblar alhajas en sucursales nuevas.

Output: Tres fixes funcionales, build de backend y frontend exitoso, módulo plazos-periodos completamente operativo.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@CLAUDE.md

# Archivos ya leídos en preparación (ver <current_state> del spec) — no requieren relectura, pero el ejecutor PUEDE consultarlos al editar:
@prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.ts
@prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.html
@prestamil-frontend/src/app/prestamil/core/services/plazo.service.ts
@prestamil-frontend/src/app/prestamil/core/models/plazo.model.ts
@prestamil-backend/src/main/java/com/ignis/prestamil/controller/PlazoController.java
@prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java

<interfaces>
<!-- Contratos clave que el ejecutor necesita. Extraídos del current_state — NO explorar el codebase para confirmarlos. -->

Frontend — plazo.model.ts (ya existente):
```typescript
// Ya definido — usar tal cual
export interface TipoPrendaRef { id: number; tipo: string; }
export interface PlazoHechuraAlhajaRequest {
  kilataje: number;
  hechura: 'F' | 'N' | 'E';
  precioBase: number;
  porcAumento: number;
}
export interface PlazoHechuraAlhajaResponse { /* incluye los campos anteriores + precioPrestamo + tablaPrestamoId */ }
export interface PlazoParametroRequest { /* % interés, % almacén, % gAdmin, % prestamoAvaluo, cat, maxRefrendos, % prestamoAvaluoReal, usaAvaluoReal, diasGraciaSinInteres, diasAntesPaseVenta, importeMinimoPrestamo */ }
export interface PlazoParametroResponse { tipoPrendaId: number; /* + mismos campos */ }
```

Frontend — plazo.service.ts (métodos existentes a NO duplicar):
```typescript
guardarParametro(plazoId: number, tipoPrendaId: number, request: PlazoParametroRequest, sucursalId: number = 1): Observable<PlazoParametroResponse>
actualizarPrecioBase(plazoId: number, kilataje: number, hechura: string, precioBase: number, sucursalId: number = 1): Observable<PlazoHechuraAlhajaResponse>
// API_URL ya es: `${environment.apiUrl}/api/plazos`
```

Backend — endpoints existentes (NO recrear):
- GET /api/prendas/tipos → TipoPrenda[]
- PUT /api/plazos/{id}/parametros/{tipoPrendaId}?sucursalId=1 (upsert ya implementado)
- PUT /api/plazos/{id}/alhajas/{kilataje}/{hechura}?sucursalId=1 (solo update — lanza 404 si no existe)

Backend — clases que existen y deben usarse:
- `PlazoHechuraAlhaja` (entity con `@EmbeddedId PlazoHechuraAlhajaId`)
- `PlazoHechuraAlhajaId` constructor: `(idPlazo, sucursalId, kilataje, hechura)`
- `PlazoHechuraAlhajaRepository` (extiende BaseRepository)
- `PlazoHechuraAlhajaMapper` — debe tener `toEntity(PlazoHechuraAlhajaRequest, PlazoHechuraAlhajaId)` y `toResponse(PlazoHechuraAlhaja)`. Si falta el `toEntity` con ese segundo parámetro, agregarlo.
- `BadRequestException` (paquete `com.ignis.prestamil.exception`)
</interfaces>

<convenciones-claves>
- Frontend: componente standalone, `inject()` style, `SharedModule` ya importado, ng-bootstrap modal ya en uso. NO usar ngbAccordion.
- Backend: `@RestController`, `@RequiredArgsConstructor`, validación con `@Valid`, indentación de 4 espacios, javadoc en español en métodos de servicio. Usar `BadRequestException` (HTTP 400) — NO `IllegalStateException`.
- DTO pattern: usar `*Request` y `*Response` existentes, no exponer entidades crudas.
- sucursalId hardcoded a 1 (decisión 2026-05-16 en STATE.md).
</convenciones-claves>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Backend — endpoint POST /api/plazos/{id}/alhajas (crear alhaja individual)</name>
  <files>
    prestamil-backend/src/main/java/com/ignis/prestamil/controller/PlazoController.java,
    prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java
  </files>
  <action>
    Agregar capacidad de crear una alhaja nueva en `plazo_hechura_alhaja` (necesario para Fix 3 — el endpoint PUT existente lanza 404 si no existe).

    **A) PlazoService.java — agregar método `crearAlhaja`:**

    Imports requeridos (verificar y agregar si faltan):
    - `import com.ignis.prestamil.exception.BadRequestException;`
    - `import com.ignis.prestamil.model.PlazoHechuraAlhaja;`
    - `import com.ignis.prestamil.model.PlazoHechuraAlhajaId;`
    - `import com.ignis.prestamil.request.PlazoHechuraAlhajaRequest;`
    - `import com.ignis.prestamil.response.PlazoHechuraAlhajaResponse;`
    - `import java.math.BigDecimal;`
    - `import java.math.RoundingMode;`

    Método nuevo (en el cuerpo de la clase, junto a los demás métodos de alhajas; respetar indentación de 4 espacios y javadoc en español):

    ```java
    /**
     * Crea una nueva combinación de alhaja para un plazo y sucursal específicos.
     * Calcula automáticamente el precioPrestamo a partir de precioBase * (1 + porcAumento).
     *
     * @param idPlazo identificador del plazo
     * @param sucursalId identificador de la sucursal
     * @param request datos de la nueva alhaja (kilataje, hechura, precioBase, porcAumento)
     * @return PlazoHechuraAlhajaResponse de la alhaja creada
     * @throws BadRequestException si ya existe la combinación plazo/sucursal/kilataje/hechura
     */
    public PlazoHechuraAlhajaResponse crearAlhaja(Integer idPlazo, Integer sucursalId, PlazoHechuraAlhajaRequest request) {
        // 1. Validar duplicado
        PlazoHechuraAlhajaId id = new PlazoHechuraAlhajaId(idPlazo, sucursalId, request.getKilataje(), request.getHechura());
        if (plazoHechuraAlhajaRepository.existsById(id)) {
            throw new BadRequestException("Ya existe una combinación " + request.getKilataje() + "K/" + request.getHechura() + " para este plazo/sucursal");
        }
        // 2. Construir entidad desde el request usando el mapper (agregar el método si no existe)
        PlazoHechuraAlhaja entity = plazoHechuraAlhajaMapper.toEntity(request, id);
        // 3. tablaPrestamoId = 1 por defecto (iteración 1 del módulo)
        entity.setTablaPrestamoId(1);
        // 4. Calcular precioPrestamo = precioBase * (1 + porcAumento)
        BigDecimal precioPrestamo = request.getPrecioBase()
                .multiply(BigDecimal.ONE.add(request.getPorcAumento()))
                .setScale(4, RoundingMode.HALF_UP);
        entity.setPrecioPrestamo(precioPrestamo);
        // 5. Guardar y mapear a response
        return plazoHechuraAlhajaMapper.toResponse(plazoHechuraAlhajaRepository.save(entity));
    }
    ```

    Si `PlazoHechuraAlhajaMapper.toEntity(PlazoHechuraAlhajaRequest, PlazoHechuraAlhajaId)` no existe, agregarlo a la interfaz del mapper (usar `@Mapping(target = "id", source = "id")` y `@Mapping(target = "precioPrestamo", ignore = true)` y `@Mapping(target = "tablaPrestamoId", ignore = true)`). Si el archivo del mapper no muestra el método, hay que agregarlo — el ejecutor debe abrir `PlazoHechuraAlhajaMapper.java` para confirmar/extender.

    **B) PlazoController.java — agregar endpoint POST:**

    Imports requeridos (verificar):
    - `import org.springframework.http.HttpStatus;`
    - `import org.springframework.web.bind.annotation.PostMapping;`
    - `import jakarta.validation.Valid;`

    Método nuevo (junto a los demás endpoints de alhajas; indentación de 4 espacios):

    ```java
    /**
     * Crea una nueva combinación de alhaja para un plazo/sucursal.
     */
    @PostMapping("/{id}/alhajas")
    public ResponseEntity<PlazoHechuraAlhajaResponse> crearAlhaja(
            @PathVariable Integer id,
            @RequestParam(defaultValue = "1") Integer sucursalId,
            @Valid @RequestBody PlazoHechuraAlhajaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(plazoService.crearAlhaja(id, sucursalId, request));
    }
    ```

    NO modificar los endpoints existentes. NO tocar `actualizarPrecioBase` ni `recalcularPreciosPorOro`.
  </action>
  <verify>
    <automated>
      cd prestamil-backend &amp;&amp; ./mvnw -q -DskipTests compile
    </automated>
    Compilación exitosa. Verificar manualmente que el endpoint responde con curl/Postman tras `./mvnw spring-boot:run`:
    - `POST http://localhost:8080/api/plazos/1/alhajas?sucursalId=1` body `{"kilataje":14,"hechura":"N","precioBase":100,"porcAumento":0.05}` → 201 con response que incluye precioPrestamo=105.0000.
    - Repetir misma petición → 400 con mensaje "Ya existe una combinación 14K/N...".
  </verify>
  <done>
    - `PlazoService.crearAlhaja` existe, valida duplicado con `BadRequestException`, calcula precioPrestamo y persiste.
    - `PlazoController` expone `POST /{id}/alhajas` con `@Valid @RequestBody PlazoHechuraAlhajaRequest`, devuelve 201.
    - `PlazoHechuraAlhajaMapper` tiene `toEntity(PlazoHechuraAlhajaRequest, PlazoHechuraAlhajaId)` disponible.
    - `mvn compile` exitoso sin warnings nuevos.
  </done>
</task>

<task type="auto">
  <name>Task 2: Frontend Fix 1 — tipos de prenda en modal (checkboxes) y lista (chips)</name>
  <files>
    prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.ts,
    prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.html
  </files>
  <action>
    Implementar Fix 1 del spec: tipos de prenda funcionales en modal y lista.

    **A) plazos-periodos.component.ts — completar `cargarTiposPrenda()`:**

    1. Verificar que `HttpClient` está inyectado vía `inject(HttpClient)`. Si no lo está, agregar:
       - `import { HttpClient } from '@angular/common/http';`
       - `import { environment } from 'src/environments/environment';`
       - Campo privado: `private http = inject(HttpClient);`
       - Campo privado: `private readonly TIPOS_PRENDA_URL = `${environment.apiUrl}/api/prendas/tipos`;` (o reutilizar el patrón ya presente en otros servicios — verificar el archivo existente).

    2. Reemplazar el body vacío de `cargarTiposPrenda()` por:
       ```typescript
       cargarTiposPrenda(): void {
         this.http.get<TipoPrendaRef[]>(`${environment.apiUrl}/api/prendas/tipos`).subscribe({
           next: (data) => { this.tiposPrenda = data ?? []; },
           error: (err) => { console.error('Error cargando tipos de prenda', err); this.tiposPrenda = []; }
         });
       }
       ```
       Asegurar que `TipoPrendaRef` está importado desde `core/models/plazo.model`.

    3. Confirmar que `cargarTiposPrenda()` se invoca en `ngOnInit()` (si no, agregar la llamada al final de ngOnInit). NO duplicar invocaciones.

    **B) plazos-periodos.component.html — modal: agregar checkboxes después de la fila de nombre/días/periodos y antes del activo:**

    ```html
    <div class="row mb-3">
      <div class="col-12">
        <label class="form-label">Tipos de prenda</label>
        <div class="d-flex flex-wrap gap-2">
          <div *ngFor="let t of tiposPrenda" class="form-check">
            <input type="checkbox" class="form-check-input"
              [id]="'tp-' + t.id"
              [checked]="isTipoPrendaMarcado(t.id)"
              (change)="onTipoPrendaCheckChange(t.id, $any($event.target).checked)" />
            <label class="form-check-label" [for]="'tp-' + t.id">{{ t.tipo }}</label>
          </div>
          <span *ngIf="tiposPrenda.length === 0" class="text-muted small">Sin tipos de prenda disponibles</span>
        </div>
      </div>
    </div>
    ```

    **C) plazos-periodos.component.html — lista: agregar chips debajo del nombre de cada plazo.**

    Localizar el bloque `*ngFor` que renderiza cada plazo en la lista (la card/item con el nombre del plazo). Insertar inmediatamente después del nombre:

    ```html
    <div class="d-flex flex-wrap gap-1 mt-1">
      <span *ngFor="let t of (plazo.tiposPrenda ?? [])" class="badge bg-light text-dark border small">
        {{ t.tipo }}
      </span>
    </div>
    ```

    No modificar la lógica TS de `isTipoPrendaMarcado`, `onTipoPrendaCheckChange` ni `tiposPrendaSeleccionados` — ya están implementadas.
  </action>
  <verify>
    <automated>
      cd prestamil-frontend &amp;&amp; npx ng build --configuration=development
    </automated>
    Build exitoso. Verificación manual con backend corriendo:
    1. Navegar a /configuracion/plazos-periodos → abrir el modal "Nuevo Plazo" → debe verse la sección "Tipos de prenda" con checkboxes poblados desde GET /api/prendas/tipos.
    2. Seleccionar 2 tipos, guardar el plazo, recargar → en la lista, debajo del nombre del plazo, aparecen 2 chips con los tipos seleccionados.
  </verify>
  <done>
    - `cargarTiposPrenda()` hace GET real a /api/prendas/tipos y pobla `this.tiposPrenda`.
    - Modal muestra checkboxes con `isTipoPrendaMarcado` / `onTipoPrendaCheckChange` ya existentes funcionando.
    - Lista de plazos muestra chips con `plazo.tiposPrenda`.
    - `ng build` exitoso sin errores ni warnings nuevos.
  </done>
</task>

<task type="auto">
  <name>Task 3: Frontend Fix 2 — Tab Parámetros editable con upsert por tipo de prenda</name>
  <files>
    prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.ts,
    prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.html
  </files>
  <action>
    Implementar Fix 2 del spec: convertir el tab Parámetros (actualmente read-only en una tabla) en formularios editables con botón "Guardar [nombre tipo]" por cada tipo de prenda asociado al plazo seleccionado.

    **A) plazos-periodos.component.ts — agregar estado:**

    Agregar como campos públicos de la clase (sobre métodos existentes):
    ```typescript
    parametrosForm: { [tipoPrendaId: number]: Partial<PlazoParametroRequest> } = {};
    savingParam: { [tipoPrendaId: number]: boolean } = {};
    paramSaveError: { [tipoPrendaId: number]: string } = {};
    paramSaveSuccess: { [tipoPrendaId: number]: boolean } = {};
    ```

    **B) Pre-popular `parametrosForm` cuando se carga el plazo seleccionado.**

    Modificar `cargarParametros()` (o donde se setean `this.parametros`): tras asignar `this.parametros = data`, ejecutar:
    ```typescript
    // Pre-popular parametrosForm: una entrada por cada tipo de prenda asociado al plazo
    this.parametrosForm = {};
    const tipos = this.selectedPlazo?.tiposPrenda ?? [];
    tipos.forEach(t => {
      const existing = this.parametros.find(p => p.tipoPrendaId === t.id);
      if (existing) {
        // Clonar a Partial<PlazoParametroRequest> sin tipoPrendaId
        this.parametrosForm[t.id] = { ...existing } as Partial<PlazoParametroRequest>;
      } else {
        // Defaults en 0 / false para registros nuevos
        this.parametrosForm[t.id] = {
          porcInteres: 0, porcAlmacen: 0, porcGAdmin: 0,
          porcPrestamoAvaluo: 0, cat: 0, maxRefrendos: 0,
          porcPrestamoAvaluoReal: 0, usaAvaluoReal: false,
          diasGraciaSinInteres: 0, diasAntesPaseVenta: 0, importeMinimoPrestamo: 0
        } as Partial<PlazoParametroRequest>;
      }
    });
    ```
    NOTA: los nombres exactos de los campos en `PlazoParametroRequest` deben verificarse abriendo `plazo.model.ts` antes de escribir el bloque de defaults. Usar los nombres EXACTOS del modelo (camelCase). Si algún campo no existe en el request, omitirlo.

    **C) Método `guardarParametro` (nuevo):**
    ```typescript
    guardarParametro(tipoPrendaId: number): void {
      if (!this.selectedPlazo) return;
      this.savingParam[tipoPrendaId] = true;
      this.paramSaveError[tipoPrendaId] = '';
      const form = this.parametrosForm[tipoPrendaId] ?? {};
      this.plazoService.guardarParametro(this.selectedPlazo.id, tipoPrendaId, form as PlazoParametroRequest, this.sucursalId).subscribe({
        next: (saved) => {
          this.savingParam[tipoPrendaId] = false;
          this.paramSaveSuccess[tipoPrendaId] = true;
          const idx = this.parametros.findIndex(p => p.tipoPrendaId === tipoPrendaId);
          if (idx >= 0) this.parametros[idx] = saved; else this.parametros.push(saved);
          setTimeout(() => { this.paramSaveSuccess[tipoPrendaId] = false; }, 3000);
        },
        error: (err) => {
          this.savingParam[tipoPrendaId] = false;
          this.paramSaveError[tipoPrendaId] = err?.error?.message ?? 'Error al guardar';
        }
      });
    }
    ```
    Verificar que `this.sucursalId` ya está definido en el componente (por la quick task 260516-mns). Si no existe, declarar `sucursalId = 1;` como campo público.

    **D) plazos-periodos.component.html — reemplazar el contenido del tab Parámetros:**

    Localizar el `ng-template` o `div` del tab Parámetros (actualmente con una `<table>` read-only) y reemplazar TODO su contenido por:

    ```html
    <ng-container *ngIf="selectedPlazo">
      <div *ngIf="(selectedPlazo.tiposPrenda?.length ?? 0) === 0" class="alert alert-info">
        Asigne tipos de prenda al plazo primero.
      </div>

      <ng-container *ngFor="let t of (selectedPlazo.tiposPrenda ?? []); let isLast = last">
        <div class="mb-3">
          <h6 class="mb-2">{{ t.tipo }}</h6>

          <!-- Fila 1 -->
          <div class="row g-2 mb-2">
            <div class="col-md-4">
              <label class="form-label small mb-0">% Interés</label>
              <input type="number" class="form-control form-control-sm" step="0.0001"
                [(ngModel)]="parametrosForm[t.id].porcInteres" />
            </div>
            <div class="col-md-4">
              <label class="form-label small mb-0">% Almacén</label>
              <input type="number" class="form-control form-control-sm" step="0.0001"
                [(ngModel)]="parametrosForm[t.id].porcAlmacen" />
            </div>
            <div class="col-md-4">
              <label class="form-label small mb-0">% G. Admin</label>
              <input type="number" class="form-control form-control-sm" step="0.0001"
                [(ngModel)]="parametrosForm[t.id].porcGAdmin" />
            </div>
          </div>

          <!-- Fila 2 -->
          <div class="row g-2 mb-2">
            <div class="col-md-4">
              <label class="form-label small mb-0">% Préstamo s/Avalúo</label>
              <input type="number" class="form-control form-control-sm" step="0.0001"
                [(ngModel)]="parametrosForm[t.id].porcPrestamoAvaluo" />
            </div>
            <div class="col-md-4">
              <label class="form-label small mb-0">CAT</label>
              <input type="number" class="form-control form-control-sm" step="0.0001"
                [(ngModel)]="parametrosForm[t.id].cat" />
            </div>
            <div class="col-md-4">
              <label class="form-label small mb-0">Max Refrendos</label>
              <input type="number" class="form-control form-control-sm"
                [(ngModel)]="parametrosForm[t.id].maxRefrendos" />
            </div>
          </div>

          <!-- Fila 3 -->
          <div class="row g-2 mb-2">
            <div class="col-md-4">
              <label class="form-label small mb-0">% Préstamo s/Avalúo Real</label>
              <input type="number" class="form-control form-control-sm" step="0.0001"
                [(ngModel)]="parametrosForm[t.id].porcPrestamoAvaluoReal" />
            </div>
            <div class="col-md-4 d-flex align-items-end">
              <div class="form-check">
                <input type="checkbox" class="form-check-input"
                  [id]="'usa-real-' + t.id"
                  [(ngModel)]="parametrosForm[t.id].usaAvaluoReal" />
                <label class="form-check-label" [for]="'usa-real-' + t.id">Usa Avalúo Real</label>
              </div>
            </div>
          </div>

          <!-- Fila 4 -->
          <div class="row g-2 mb-2">
            <div class="col-md-4">
              <label class="form-label small mb-0">Días gracia sin interés</label>
              <input type="number" class="form-control form-control-sm"
                [(ngModel)]="parametrosForm[t.id].diasGraciaSinInteres" />
            </div>
            <div class="col-md-4">
              <label class="form-label small mb-0">Días antes pase a venta</label>
              <input type="number" class="form-control form-control-sm"
                [(ngModel)]="parametrosForm[t.id].diasAntesPaseVenta" />
            </div>
            <div class="col-md-4">
              <label class="form-label small mb-0">Importe mínimo préstamo</label>
              <input type="number" class="form-control form-control-sm" step="0.01"
                [(ngModel)]="parametrosForm[t.id].importeMinimoPrestamo" />
            </div>
          </div>

          <!-- Acciones -->
          <div class="d-flex align-items-center gap-2">
            <button class="btn btn-sm btn-primary" (click)="guardarParametro(t.id)" [disabled]="savingParam[t.id]">
              <span *ngIf="savingParam[t.id]" class="spinner-border spinner-border-sm me-1"></span>
              Guardar {{ t.tipo }}
            </button>
            <span *ngIf="paramSaveSuccess[t.id]" class="text-success small">
              <i class="feather icon-check"></i> Guardado
            </span>
            <span *ngIf="paramSaveError[t.id]" class="text-danger small">
              {{ paramSaveError[t.id] }}
            </span>
          </div>
        </div>
        <hr *ngIf="!isLast" />
      </ng-container>
    </ng-container>
    ```

    CRÍTICO: si algún nombre de campo del request difiere del modelo real (`porcInteres`, `porcAlmacen`, etc.), AJUSTAR los `[(ngModel)]` para que coincidan con el modelo. Abrir `plazo.model.ts` antes de la edición para confirmar.

    NO usar ngbAccordion (per constraint del spec). Los formularios son simples divs separados por `<hr>`.
  </action>
  <verify>
    <automated>
      cd prestamil-frontend &amp;&amp; npx ng build --configuration=development
    </automated>
    Build exitoso. Verificación manual con backend corriendo:
    1. Seleccionar un plazo con tipos de prenda asignados → tab Parámetros muestra un formulario por cada tipo, separados por `<hr>`.
    2. Modificar un valor, hacer click en "Guardar [tipo]" → aparece spinner, luego "Guardado" en verde. Recargar el plazo → el valor persiste.
    3. Para un tipo sin parámetro previo, modificar y guardar → debe crearse (upsert ya implementado en backend).
    4. Plazo sin tiposPrenda asignados → muestra mensaje "Asigne tipos de prenda al plazo primero".
  </verify>
  <done>
    - Estado `parametrosForm`, `savingParam`, `paramSaveError`, `paramSaveSuccess` declarado.
    - `cargarParametros()` pre-popula `parametrosForm` con datos existentes o defaults en 0.
    - Método `guardarParametro(tipoPrendaId)` llama a `plazoService.guardarParametro` y actualiza `this.parametros` en sitio.
    - Tab Parámetros muestra formularios editables por tipo de prenda separados por `<hr>` con botón individual.
    - Mensaje "Asigne tipos de prenda primero" cuando no hay tipos asociados.
    - `ng build` exitoso.
  </done>
</task>

<task type="auto">
  <name>Task 4: Frontend Fix 3 — agregar/inicializar alhajas (botón estándar + form individual)</name>
  <files>
    prestamil-frontend/src/app/prestamil/core/services/plazo.service.ts,
    prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.ts,
    prestamil-frontend/src/app/prestamil/pages/configuracion/plazos-periodos/plazos-periodos.component.html
  </files>
  <action>
    Implementar Fix 3 del spec: permitir crear filas individuales y inicializar tabla estándar de 12 combinaciones.

    **A) plazo.service.ts — agregar método `crearAlhaja`:**

    Junto a los métodos existentes de alhajas:
    ```typescript
    crearAlhaja(
      plazoId: number,
      request: PlazoHechuraAlhajaRequest,
      sucursalId: number = 1
    ): Observable<PlazoHechuraAlhajaResponse> {
      return this.http.post<PlazoHechuraAlhajaResponse>(
        `${this.API_URL}/${plazoId}/alhajas`,
        request,
        { params: { sucursalId: String(sucursalId) } }
      );
    }
    ```
    Verificar que `PlazoHechuraAlhajaRequest` y `PlazoHechuraAlhajaResponse` ya estén importados desde `../models/plazo.model`.

    **B) plazos-periodos.component.ts — agregar estado y métodos:**

    Imports a verificar/agregar:
    - `import { forkJoin } from 'rxjs';`
    - `PlazoHechuraAlhajaRequest` desde `core/models/plazo.model`.

    Campos públicos nuevos:
    ```typescript
    nuevaAlhaja: Partial<PlazoHechuraAlhajaRequest> = { kilataje: 14, hechura: 'N', precioBase: 0, porcAumento: 0 };
    isAgregandoAlhaja = false;
    isInicializando = false;
    alhajaError = '';
    ```

    Métodos nuevos:
    ```typescript
    agregarAlhaja(): void {
      if (!this.selectedPlazo) return;
      this.isAgregandoAlhaja = true;
      this.alhajaError = '';
      const req = this.nuevaAlhaja as PlazoHechuraAlhajaRequest;
      this.plazoService.crearAlhaja(this.selectedPlazo.id, req, this.sucursalId).subscribe({
        next: (created) => {
          this.isAgregandoAlhaja = false;
          this.alhajas = [...this.alhajas, created];
          // Reset form a defaults
          this.nuevaAlhaja = { kilataje: 14, hechura: 'N', precioBase: 0, porcAumento: 0 };
        },
        error: (err) => {
          this.isAgregandoAlhaja = false;
          this.alhajaError = err?.error?.message ?? 'Error al agregar alhaja';
        }
      });
    }

    inicializarTablaEstandar(): void {
      if (!this.selectedPlazo) return;
      this.isInicializando = true;
      this.alhajaError = '';
      const kilatajes = [10, 14, 18, 24];
      const hechuras: Array<'F' | 'N' | 'E'> = ['F', 'N', 'E'];
      const requests = kilatajes.flatMap(k => hechuras.map(h =>
        this.plazoService.crearAlhaja(this.selectedPlazo!.id, {
          kilataje: k, hechura: h, precioBase: 0, porcAumento: 0
        }, this.sucursalId)
      ));
      forkJoin(requests).subscribe({
        next: (results) => {
          this.isInicializando = false;
          this.alhajas = results;
        },
        error: (err) => {
          this.isInicializando = false;
          this.alhajaError = err?.error?.message ?? 'Error al inicializar tabla estándar';
        }
      });
    }
    ```

    **C) plazos-periodos.component.html — tab Alhajas:**

    Localizar el contenido del tab Alhajas (actualmente tabla con `precioBase` editable por fila).

    1. **Cuando `alhajas.length === 0`**, agregar bloque vacío + botón inicializar (antes de la tabla; envolver la tabla en `*ngIf="alhajas.length > 0"`):
    ```html
    <div *ngIf="alhajas.length === 0" class="text-center py-3">
      <p class="text-muted small mb-2">Sin registros de alhajas para esta sucursal.</p>
      <button class="btn btn-sm btn-outline-primary" (click)="inicializarTablaEstandar()" [disabled]="isInicializando">
        <span *ngIf="isInicializando" class="spinner-border spinner-border-sm me-1"></span>
        <i class="feather icon-grid" *ngIf="!isInicializando"></i>
        Inicializar tabla estándar (12 combinaciones)
      </button>
      <small class="d-block text-muted mt-1">Crea 10K/14K/18K/24K × Fina/Normal/Especial con precio 0</small>
    </div>
    ```

    2. **Formulario "Agregar fila" siempre visible** (debajo de la tabla, o tras el bloque inicializar si tabla vacía):
    ```html
    <div *ngIf="selectedPlazo" class="mt-3 border-top pt-3">
      <h6 class="small mb-2">Agregar alhaja</h6>
      <div class="d-flex gap-2 align-items-end flex-wrap">
        <div>
          <label class="form-label small mb-0">Kilataje</label>
          <select class="form-select form-select-sm" [(ngModel)]="nuevaAlhaja.kilataje">
            <option [ngValue]="10">10K</option>
            <option [ngValue]="14">14K</option>
            <option [ngValue]="18">18K</option>
            <option [ngValue]="24">24K</option>
          </select>
        </div>
        <div>
          <label class="form-label small mb-0">Hechura</label>
          <select class="form-select form-select-sm" [(ngModel)]="nuevaAlhaja.hechura">
            <option value="F">Fina</option>
            <option value="N">Normal</option>
            <option value="E">Especial</option>
          </select>
        </div>
        <div>
          <label class="form-label small mb-0">Precio Base</label>
          <input type="number" class="form-control form-control-sm"
            [(ngModel)]="nuevaAlhaja.precioBase" min="0" style="width:110px" />
        </div>
        <div>
          <label class="form-label small mb-0">% Aumento</label>
          <input type="number" class="form-control form-control-sm"
            [(ngModel)]="nuevaAlhaja.porcAumento" min="0" step="0.0001" style="width:110px" />
        </div>
        <button class="btn btn-sm btn-success" (click)="agregarAlhaja()" [disabled]="isAgregandoAlhaja">
          <span *ngIf="isAgregandoAlhaja" class="spinner-border spinner-border-sm me-1"></span>
          <i class="feather icon-plus" *ngIf="!isAgregandoAlhaja"></i> Agregar
        </button>
      </div>
      <div *ngIf="alhajaError" class="text-danger small mt-1">{{ alhajaError }}</div>
    </div>
    ```

    NO eliminar ni alterar la tabla existente con edición de `precioBase` por fila — solo envolverla en `*ngIf="alhajas.length > 0"` para que oculte cuando esté vacía.
  </action>
  <verify>
    <automated>
      cd prestamil-frontend &amp;&amp; npx ng build --configuration=development
    </automated>
    Build exitoso. Verificación manual end-to-end (backend + frontend corriendo):
    1. Seleccionar plazo cuya combinación (plazo, sucursal=1) no tiene alhajas → tab Alhajas muestra mensaje + botón "Inicializar tabla estándar".
    2. Click en botón → spinner → al terminar, la tabla muestra 12 filas (10K/14K/18K/24K × F/N/E).
    3. Llenar el formulario "Agregar alhaja" con una combinación distinta (ej. 22K/F) → click Agregar → la fila aparece en la tabla.
    4. Intentar agregar una combinación duplicada → mensaje rojo "Ya existe una combinación...".
  </verify>
  <done>
    - `plazoService.crearAlhaja(plazoId, request, sucursalId=1)` existe y hace POST a `/api/plazos/{id}/alhajas`.
    - Componente tiene `nuevaAlhaja`, `isAgregandoAlhaja`, `isInicializando`, `alhajaError`, `agregarAlhaja()`, `inicializarTablaEstandar()`.
    - HTML muestra botón inicializar cuando `alhajas.length === 0` y formulario agregar siempre que haya plazo seleccionado.
    - `forkJoin` ejecuta 12 peticiones en paralelo en inicialización.
    - Errores del backend (incl. 400 de duplicado) se muestran en `alhajaError`.
    - `ng build` exitoso.
  </done>
</task>

</tasks>

<verification>
**Tras completar las 4 tareas:**

1. Backend compila: `cd prestamil-backend && ./mvnw -q -DskipTests compile` → BUILD SUCCESS.
2. Frontend compila: `cd prestamil-frontend && npx ng build --configuration=development` → sin errores.
3. Smoke end-to-end con backend corriendo:
   - Modal nuevo plazo → checkboxes de tipos de prenda visibles y funcionales.
   - Plazo guardado → chips de tipos de prenda visibles en la lista.
   - Tab Parámetros → formulario por tipo asociado, guardado individual funciona (crea o actualiza).
   - Tab Alhajas (sucursal sin datos) → botón "Inicializar tabla estándar" puebla 12 combinaciones.
   - Tab Alhajas → formulario "Agregar" crea una fila individual; combinaciones duplicadas devuelven error visible.
</verification>

<success_criteria>
- [ ] Fix 1: `cargarTiposPrenda()` consume GET /api/prendas/tipos; checkboxes en modal; chips en lista.
- [ ] Fix 2: Tab Parámetros editable por tipo de prenda con upsert (crea o actualiza vía endpoint existente).
- [ ] Fix 3: POST /api/plazos/{id}/alhajas implementado en backend; método `crearAlhaja` en plazo.service.ts; UI con botón inicializar (12 combinaciones) + formulario agregar individual.
- [ ] Validación de duplicados en backend (`BadRequestException` 400) y mensaje visible en frontend.
- [ ] `mvn compile` y `ng build` exitosos.
- [ ] No se alteran endpoints existentes (PUT parametros, PUT alhajas precioBase, PUT alhajas precio-oro).
- [ ] No se introducen entidades crudas como `@RequestBody` en el nuevo endpoint — se usa `PlazoHechuraAlhajaRequest`.
</success_criteria>

<output>
After completion, create `.planning/quick/260516-oio-tres-fixes-en-plazos-periodos-tipos-de-p/260516-oio-SUMMARY.md` con:
- Resumen de los 3 fixes aplicados
- Archivos modificados con líneas agregadas/cambiadas
- Resultado de `mvn compile` y `ng build`
- Notas de verificación manual realizada
- Pendientes (si hay), incluyendo:
  - sucursalId sigue hardcoded a 1 (decisión pre-existente en STATE.md)
  - `tablaPrestamoId = 1` por defecto en crearAlhaja (iteración 1; revisar si en el futuro hay multi-tabla)
</output>
