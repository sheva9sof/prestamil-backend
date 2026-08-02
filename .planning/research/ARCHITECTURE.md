# Architecture Research — v1.1 Motor de Cálculo Real y Ciclo de Vida del Contrato

**Domain:** Pawnshop management system (Prestamil) — subsequent-milestone feature integration
**Researched:** 2026-07-02
**Confidence:** HIGH (based on direct inspection of current source, not the stale `.planning/codebase/*` docs, which are dated 2026-05-22 and understate how much of this milestone is already built)

---

## 0. Critical Finding — The codebase is further along than PROJECT.md / codebase docs suggest

`.planning/PROJECT.md` and `.planning/codebase/ARCHITECTURE.md` / `AVALUOS.md` were last analyzed **2026-05-22**. Liquibase changeset `011-oro-sancion-plata.sql` (dated **2026-06-08**) and the current state of `PlazoService`, `ContratoService`, `MovimientoContratoService`, `PlazoParametro`, `MovimientoContrato`, and `Contrato` show that **6 of the 8 target features already have a first implementation**. This changes the roadmap materially: most of this milestone is refinement/correction of existing code, not net-new module construction. Treat the "Active" checklist in PROJECT.md as partially stale.

| # | Target feature (PROJECT.md) | Actual state found in code | Verdict |
|---|---|---|---|
| 1 | Motor de oro con tabla real de 24 celdas | `PlazoHechuraAlhaja` (8 kilataje × 3 hechura = 24 rows/plazo/sucursal) already exists and is seeded correctly. **But** `PlazoService.recalcularRegistros()` overwrites `precioBase` for all 24 cells using 3 *global* hechura factors (`factorFundir`/`factorNormal`/`factorEspecial`, default 90/100/110%) applied uniformly across all 8 kilatajes — exactly the "known wrong" behavior PROJECT.md flags. | **GAP — logic only, no schema change needed** |
| 2 | Tabla de precios de plata (leyes 925/725) | `PlazoParametro` already has `ley925`, `ley725`, `precioGramoPlata` columns (changeset 011). `PartidaContrato.ley` and `PartidaContratoRequest.ley` already exist. **But** none of these fields are read anywhere in `ContratoService`/`PlazoService` — grep for `getLey925/getLey725/getPrecioGramoPlata` returns zero hits in `service/`. Silver partidas are currently priced exactly like Varios (client sends `avaluoReal` manually, server only caps it). | **GAP — calculation not wired; no new entity needed (see §2)** |
| 3 | Varios/electrónicos con lógica propia | Already implemented: `ContratoService.calcularPrestamoMaximo()` treats any tipo de prenda without a `PlazoHechuraAlhaja` lookup as free-form — caps `pr.getAvaluoReal()` by `PlazoParametro.porcPrestamoSAvaluo` if configured, else uses `avaluoReal` itself. | **DONE** |
| 4 | Tabla de amortización al vuelo | `ContratoService.calcularAmortizacion()` + `GET /api/contratos/{id}/amortizacion` + `VencimientoResponse` already implemented. Computes fecha/interés/total per periodo from `fechaApertura` + `Plazo.diasPorPeriodo`/`numeroPeriodos`, no intermediate dates persisted. | **DONE (needs sanción integration — see §3.3)** |
| 5 | Sanción 2%/semana en refrendo tardío | `MovimientoContratoService.refrendar()` fully implemented: computes `semanasVencidas` from `fechaVencimiento` + `diasGraciaSinInteres`, applies `porcSancionSemanal` (default 2.0000, per `PlazoParametro`), tags movement `REFRENDO_EXTEMPORANEO` vs `REFRENDO`. | **DONE** |
| 6 | Folio autogenerado consecutivo | `ContratoService.crearContrato()` does a two-phase save: insert to get the identity `id`, then `folio = "CTR-%06d".formatted(id)`. | **DONE (functionally), but see pitfall §7 — folio is derived from a shared AUTO_INCREMENT, not a dedicated sequence** |
| 7 | Reposición de contrato con registro en caja | `MovimientoContratoService.cobrarReposicion()` + `POST /api/movimientos/reposicion/{contratoId}` implemented; reads `cobrarReposicionContrato`/`reposicionEsPorcentaje`/`porcReposicion`/`montoReposicion` from `PlazoParametro`, writes a `MovimientoContrato` with `tipo = REPOSICION_CONTRATO` tied to the active `Turno`. | **DONE at the ledger level — but no report/PDF consumes `MovimientoContrato` yet, so the "bug conocido" (reposición not reflected in reportes de caja) cannot be verified fixed: there is currently no caja/turno report endpoint at all (see §6)** |
| 8 | Beneficiario obligatorio | `Contrato.beneficiario` (`@JoinColumn(name="id_beneficiario")`, no `nullable=false`) and `ContratoRequest.idBeneficiario` (no `@NotNull`) are still optional. Schema (`007-contratos.sql`) still declares `id_beneficiario INT` nullable. | **GAP — small, but touches DB constraint + request validation + existing rows** |

**Also found, not explicitly in PROJECT.md but relevant:**
- `PrecioOro` entity/table (changeset 011) centralizes gold price + hechura factors per sucursal, with `PlazoController`/`PlazoService.recalcularTodasLasTablas()` recalculating every plazo's `PlazoHechuraAlhaja` rows for that sucursal in one call. This is the natural integration point for the real 24-cell import (§3.1).
- No PDF/JasperReports wiring exists yet despite `resources/jasper/contrato.jrxml` being present — `PrinterService.printTicket()` is still a `System.out.println` stub, and no controller calls `JasperFillManager`/`JasperExportManager`. The "Reporte/PDF de referencia" and "reimpresión de contrato" features are **not started**.
- No cash-register/turno report endpoint exists (`grep -rln "corteCaja|reporte" controller/ service/` → no hits). This is the actual root cause of the "bug conocido" — there's nothing yet that reads `MovimientoContrato` for a report, so the discrepancy PROJECT.md describes must be fixed by *building* the report against the now-correct `MovimientoContrato` ledger, not by patching an existing broken one.
- Frontend has no dedicated route/page for refrendos or reposición yet (`app-routing.module.ts` has no `/refrendos` or `/movimientos` path). `movimiento.service.ts` exists in `core/services/` but nothing in `pages/` consumes it — confirm before assuming a UI screen exists.

---

## 1. System Overview (existing, unchanged)

```
┌──────────────────────────────────────────────────────────────────────┐
│ Angular 20 SPA (standalone components, lazy-loaded via loadComponent) │
│  pages/avaluos/avaluo/avaluo.component.ts  ← primary UI touch point   │
├──────────────────────────────────────────────────────────────────────┤
│ HttpClient + CredentialsInterceptor (withCredentials) + JSESSIONID    │
├──────────────────────────────────────────────────────────────────────┤
│ Controller (@RestController, thin)                                    │
│  PlazoController · ContratoController · MovimientoContratoController  │
│  PrecioOroController                                                  │
├──────────────────────────────────────────────────────────────────────┤
│ Service (@Transactional, business logic)                              │
│  PlazoService (oro) · ContratoService (contrato+partidas) ·           │
│  MovimientoContratoService (refrendo/reposición/sanción)              │
├──────────────────────────────────────────────────────────────────────┤
│ Repository (Spring Data JPA, extends BaseRepository<T,ID>)            │
├──────────────────────────────────────────────────────────────────────┤
│ MariaDB — Liquibase changesets 001–011 in db/changelog/changes/       │
└──────────────────────────────────────────────────────────────────────┘
```

No new layer, no new cross-cutting pattern is required for this milestone. Every gap below is closed by adding methods to existing services or, at most, one new small entity — not by introducing a new architectural pattern.

---

## 2. New Entities/Tables Needed vs Reuse — explicit per feature

| Feature | New table/entity? | Decision & rationale |
|---|---|---|
| Oro (24-cell) | **No new table.** `PlazoHechuraAlhaja` + `PlazoHechuraAlhajaId` (key: `idPlazo, sucursalId, kilataje, hechura`) already model exactly an 8×3 grid. | Fix is a **new Liquibase changeset only if** you need to widen `porc_aumento` further or add an `origen` flag (`IMPORTADO` vs `CALCULADO`) to distinguish COCAE-imported cells from formula-derived ones — recommended (see §3.1). No new entity class. |
| Plata (leyes 925/725) | **No new table recommended.** `PlazoParametro.ley925/ley725/precioGramoPlata` already exist and are the right shape: silver has exactly 2 discrete leyes (not a range like oro's 8 kilatajes), so a `PlazoHechuraAlhaja`-style 24-cell entity would be over-engineering. | Add a **service method**, not a new entity. If the business later needs per-sucursal *and* per-plazo silver margin tiers beyond a flat `precioGramoPlata`, revisit — but nothing in AVALUOS.md/PROJECT.md indicates that need today. |
| Varios | No new entity — already correctly modeled as free-form `avaluoReal` capture, capped by existing `PlazoParametro.porcPrestamoSAvaluo`. | No action. |
| Amortización | No new table — intentionally computed on the fly (`VencimientoResponse` is a plain response DTO, not persisted). | No action; do not persist intermediate vencimientos (PROJECT.md explicitly forbids this). |
| Sanción | No new table — `MovimientoContrato.sancion`/`semanasVencidas` columns already added (changeset 011-7). | No action. |
| Folio | No new table — currently derived from `Contrato.id` AUTO_INCREMENT. | Optional: if concurrent branches/sucursales will eventually need independent folio series, introduce a `folio_secuencia` table (`sucursal_id`, `siguiente_numero`) — **not required for current single-sucursal deployment** (`CorsConfig` and `Sucursal` are effectively single-branch by design per `.planning/codebase/STRUCTURE.md`). Flag as future work, not this milestone. |
| Reposición | No new table — `TipoMovimiento.REPOSICION_CONTRATO` + `MovimientoContrato` already sufficient. | Missing piece is a **report**, not a table (see §3.5). |
| Beneficiario obligatorio | **Schema change, no new table.** `contrato.id_beneficiario` needs a data-backfill + `NOT NULL` constraint. | New Liquibase changeset (012). |
| PDF / reimpresión | No new entity. Needs a `ContratoReporteService` (or similar) wired to the existing `contrato.jrxml`/`contrato.jasper` under `resources/jasper/`. | New service + controller endpoint, reusing existing `Contrato`/`PartidaContrato`/`MovimientoContrato` data. |
| Corte de caja / reporte | **Possibly a new lightweight aggregation, not a new entity.** Can be a read-only query over `MovimientoContrato` + `Contrato` filtered by `Turno`, exposed via a new `ReporteController`/`ReporteService`. No new table needed since `MovimientoContrato` already carries `turno`, `tipo`, `monto`, `interes`, `sancion`, `abonoCapital`. | New service/controller only. |

**Net new Liquibase changesets for this milestone:** likely just **one** (012 — beneficiario NOT NULL + backfill), possibly a second small one if you add the `origen` flag to `plazo_hechura_alhaja` for oro data provenance. Everything else is service-layer logic against the existing schema.

---

## 3. Integration Points by Feature (concrete class names)

### 3.1 Motor de oro — replace factor-derived recalculation with real per-cell data

- **Touch:** `PlazoService.recalcularRegistros()` (private, called by both `actualizarTodosPrecios()` and `recalcularTodasLasTablas()`).
- **Current bug:** `precioBase` for every cell is computed as `(precioGramoBase / baseKilataje) * kilataje * factorHechura`, where `factorHechura` comes from just 3 global values (`factorFundir`/`factorNormal`/`factorEspecial` on `PrecioOro`). This produces a clean linear table — not COCAE's irregular real one.
- **Fix approach:** Stop deriving hechura differentiation from the 3 global factors. Each `PlazoHechuraAlhaja` row already carries its own `porcAumento` — that's the correct per-cell lever. Change `recalcularRegistros()` to:
  1. Recompute `precioBase(kilataje) = (precioGramoBase / baseKilataje) * kilataje` — pure linear scaling by kilataje only (no hechura factor).
  2. Compute `precioPrestamo = precioBase * (1 + porcAumento/100)` using **each row's own, independently-imported `porcAumento`**, not a shared 90/100/110 factor.
  3. Provide an admin import path (bulk upsert endpoint or CSV/JSON import into `PlazoHechuraAlhaja.porcAumento` per kilataje×hechura cell) so ops can enter COCAE's real 24 values once per plazo/sucursal. `PlazoService.crearAlhaja()` and `actualizarPrecioBase()` already exist as the per-cell write path — reuse them; add a bulk variant if entering 24 rows one at a time is impractical for onboarding.
- **Do not delete** `PrecioOro.factorFundir/factorNormal/factorEspecial` yet — they may still be useful as *default seed values* when creating a brand-new plazo before real data is imported, but they must stop being applied on every recalculation once real data exists. Recommended: add `plazo_hechura_alhaja.origen ENUM('IMPORTADO','CALCULADO')` so `recalcularRegistros()` can skip cells marked `IMPORTADO` (preserve their `porcAumento` exactly) and only formula-derive cells still marked `CALCULADO`.
- **Mapper:** `PlazoHechuraAlhajaMapper` — no structural change needed, only if the `origen` column is added.
- **Controller:** existing `PUT /api/plazos/{id}/alhajas/precio-oro` (→ `recalcularTodasLasTablas`) stays the entry point; no new route required unless a bulk-import endpoint is added (e.g., `PUT /api/plazos/{id}/alhajas/importar?sucursalId=1` accepting a list of 24 `{kilataje, hechura, porcAumento}`).

### 3.2 Motor de plata — wire existing PlazoParametro fields into calculation

- **Touch:** `ContratoService.buildPartida()` / `calcularPrestamoMaximo()`.
- **Current gap:** `PartidaContratoRequest.ley` is accepted but unused server-side; `avaluoReal` for plata partidas is trusted from the client, same as Varios.
- **Fix approach:** Add a service method analogous to the oro path:
  ```java
  private BigDecimal calcularAvaluoPlata(BigDecimal pesoGramos, BigDecimal ley, PlazoParametro parametro) {
      BigDecimal factorLey = ley.compareTo(new BigDecimal("925")) == 0
              ? parametro.getLey925() : parametro.getLey725();
      return pesoGramos.multiply(parametro.getPrecioGramoPlata())
              .multiply(factorLey.divide(CIEN, 10, RoundingMode.HALF_UP))
              .setScale(2, RoundingMode.HALF_UP);
  }
  ```
  Call this from `buildPartida()` when `tipoPrenda` corresponds to `PLATAS` (id 4 in `tipo_prenda`, per `AVALUOS.md` §1.1) and `pr.getLey()`/`pr.getPesoGramos()` are present, producing a server-trusted `avaluoReal` instead of accepting the client's value verbatim (mirrors how oro already refuses to trust client-computed values — see `calcularPrestamoMaximo` comment "Avalúo del contrato: lo fija el servidor").
- **Frontend:** `avaluo.component.ts` already has state branching for "ALHAJAS/PLATA" (per the comment found at line ~246) — confirm it currently posts a manually-typed `avaluoReal` for PLATA and switch it to send `pesoGramos` + `ley` and let the server compute, consistent with the ALHAJA flow which already relies on `PlazoHechuraAlhajaService`-sourced pricing rather than manual entry.

### 3.3 Amortización — integrate sanción display

- `ContratoService.calcularAmortizacion()` currently only projects `interesPeriodo` forward; it does not account for sanción-by-extemporaneidad in the *projected* schedule (sanción is only computed reactively in `MovimientoContratoService.refrendar()` at the moment of payment). This is architecturally correct — sanción is a real-time computation based on "today", not a static schedule value — but confirm the frontend "Reporte/PDF de referencia" (feature target, not started) doesn't assume sanción appears in the amortization table; it should instead show the *current* sanción-if-paid-today as a separate computed field sourced from the same formula `MovimientoContratoService.calcularSemanasVencidas()` uses. Consider extracting that formula into a shared/static helper (or into `PlazoService`) so both `ContratoService` (amortización preview) and `MovimientoContratoService` (actual refrendo) use one code path — currently the semanas-vencidas logic lives only in `MovimientoContratoService`, and there's a risk of divergence if the amortización endpoint needs to show "sanción si paga hoy" later.

### 3.4 Beneficiario obligatorio

- **Backend:**
  - `Contrato.beneficiario`: add `nullable = false` to `@JoinColumn`.
  - `ContratoRequest.idBeneficiario`: add `@NotNull`.
  - `ContratoService.crearContrato()`: the current `if (request.getIdBeneficiario() != null)` branch becomes unconditional (remove the null-check, let `@Valid` on the controller reject missing values with 400 before reaching the service).
  - **Migration (Liquibase 012):** backfill existing `contrato` rows where `id_beneficiario IS NULL` (e.g., set to `id_cliente` of the same row as a safe default — the titular acting as their own beneficiary) before adding the `NOT NULL` constraint, or the migration will fail against existing data.
- **Frontend:** `avaluo.component.ts` beneficiario field/modal must become required in the reactive form; confirm `ContratoRequest` payload always includes `idBeneficiario` before submit.

### 3.5 Reposición → caja/reportes

- **Backend:** `MovimientoContratoService.cobrarReposicion()` is already correct and turno-scoped. What's missing is a consumer: build `ReporteService.getCorteCaja(Long turnoId)` that aggregates `MovimientoContratoRepository` rows by `turno` and `tipo` (including `REPOSICION_CONTRATO`), likely returning per-`TipoMovimiento` subtotals plus a grand total, to be shown when `TurnoController.cerrarTurno()` closes a shift. New: `ReporteController` (`GET /api/reportes/corte-caja/{turnoId}`), `ReporteService`, response DTO `CorteCajaResponse`.
- **Integration point with existing turno flow:** `TurnoService.cerrarTurno()` currently just flips `activo=false` and broadcasts SSE `turno-cerrado` — it does not compute or attach a caja summary. Decide whether the corte-de-caja report is fetched separately by the frontend before/after closing (simplest, no change to `TurnoService`) or embedded into the close response (requires `TurnoResponse` to carry a `CorteCajaResponse` and `TurnoService` to depend on `ReporteService`). **Recommend the separate-fetch approach** to avoid coupling `TurnoService` to contract/movement internals — consistent with the existing layering where `TurnoService` has no knowledge of `Contrato`/`MovimientoContrato` today.

### 3.6 PDF / reimpresión de contrato

- **Backend:** new `ContratoReporteService` (or a method on `ContratoService`) using `JasperFillManager.fillReport(...)` against `resources/jasper/contrato.jasper` (compiled) or `.jrxml` (compile-on-the-fly — avoid this in production; keep pre-compiled `.jasper`), fed with `Contrato` + `PartidaContrato` data mapped into whatever fields `contrato.jrxml` expects (inspect the `.jrxml` field names before implementing — not covered by this research pass). New endpoint `GET /api/contratos/{id}/pdf` returning `application/pdf` (`ResponseEntity<byte[]>` or `StreamingResponseBody`).
- **Reimpresión** should call `MovimientoContratoService.cobrarReposicion()` first (if configured to charge) and then the same PDF generation path — i.e., reimpresión = reposición charge (conditional) + PDF regeneration, not a separate code path. This directly resolves the "bug conocido" by construction: if both operations go through the same `ContratoController`/`MovimientoContratoController` and the new report reads `MovimientoContrato`, there is no way for the charge and the caja total to diverge.

---

## 4. Data Flow Changes

### 4.1 Gold price recalculation (corrected)

```
Admin sets precio_gramo_oro_24k + imports/edits 24 real % cells (por plazo/sucursal)
        │
        ▼
PUT /api/plazos/{id}/alhajas/precio-oro  (existing)     PUT .../alhajas/{kilataje}/{hechura} (existing, per-cell)
        │                                                         │
        ▼                                                         ▼
PlazoService.recalcularTodasLasTablas()              PlazoService.actualizarPrecioBase()
        │  (fixed: no longer applies factorFundir/Normal/Especial globally)
        ▼
PlazoService.recalcularRegistros()  →  precioBase = f(kilataje only); precioPrestamo = precioBase × (1 + row.porcAumento/100)
        │
        ▼
PlazoHechuraAlhaja rows saved (24 per plazo/sucursal) — each cell's imported % preserved
        │
        ▼
ContratoService.buildPartida() reads PlazoHechuraAlhaja for the partida's kilataje+hechura → avaluoReal
```

### 4.2 Refrendo with sanción (already correct, shown for reference)

```
POST /api/movimientos/refrendo {idContrato, abonoCapital?}
        │
        ▼
MovimientoContratoService.refrendar()
   ├─ obtenerParametro(contrato)               → PlazoParametro (interés, gracia, sanción%)
   ├─ calcularSemanasVencidas(fechaVenc, param) → int
   ├─ interes = montoPrestamo × porcInteresTotal / 100
   ├─ sancion = montoPrestamo × porcSancionSemanal/100 × semanasVencidas   (if aplicarSancionPorPeriodo)
   ├─ persist MovimientoContrato (tipo REFRENDO | REFRENDO_EXTEMPORANEO)
   └─ contrato.fechaVencimiento += diasPorPeriodo; numRefrendos++; estatus=VIGENTE
```

### 4.3 New: corte de caja (to be built)

```
GET /api/reportes/corte-caja/{turnoId}
        │
        ▼
ReporteService.getCorteCaja(turnoId)
   └─ MovimientoContratoRepository.findByTurnoId(turnoId)  (new derived query, or reuse + filter)
        groupBy tipo → sum(monto), sum(interes), sum(sancion), sum(abonoCapital)
        + count/sum from Contrato created within the same turno (préstamos nuevos)
        │
        ▼
CorteCajaResponse { totalPrestamosNuevos, totalRefrendos, totalReposiciones, totalAbonos, granTotal }
```

---

## 5. Suggested Build Order (dependency-aware)

Given most items are already implemented, order by **what blocks correctness of downstream numbers**, not by naive feature listing:

1. **Motor de oro — real 24-cell fix** (`PlazoService.recalcularRegistros`, optional `origen` column). **Do this first.** Every avalúo/préstamo amount for ALHAJAS, every amortización row, and any future PDF/report will embed wrong numbers until this is fixed. This is also the only item PROJECT.md flags as blocking ("Fidelidad de cálculo" constraint) and the only one still pending real data (per PROJECT.md Context: "Falta confirmar si esa tabla es global o varía por plazo/tabla en COCAE").
2. **Beneficiario obligatorio** (schema + validation). Trivial, isolated, no dependency on anything else — do it early to stop accumulating more nullable-beneficiario contracts while other work is in progress.
3. **Motor de plata** (`ContratoService.calcularAvaluoPlata`). Depends on nothing else in this list; can be done in parallel with #1 by a different work stream since it touches a different code path (`buildPartida` branch) and different fields (`ley925/ley725/precioGramoPlata` vs `PlazoHechuraAlhaja`).
4. **Corte de caja report** (`ReporteService`/`ReporteController`). Should come *after* #1–#3 are stable, because the report's whole value is showing trustworthy totals — building it against still-wrong oro numbers means re-verifying it later. Also depends on `MovimientoContrato` schema, which is already complete (no blocker there).
5. **PDF / reimpresión de contrato**. Depends on #2 (beneficiario must be present and required before it's safe to assume it's always printable) and benefits from #1/#3 being correct (so printed avalúo/préstamo amounts are trustworthy) and from #4 existing conceptually (reimpresión's cash-register correctness argument is strongest once corte de caja actually reads `MovimientoContrato`).
6. **Frontend wiring** for each of the above should trail its backend counterpart by design (existing convention in this codebase — see `AVALUOS.md` §6 "Fase A backend / Fase B frontend / Fase C PDF"), but given #1, #3, #4 backend pieces are independent, their frontend work can proceed in parallel once each backend piece lands, rather than waiting for all five backend items sequentially.

**Explicit answer to "does amortización need the gold engine done first":** No — `calcularAmortizacion()` only consumes `Contrato.montoPrestamo` (already persisted) and `PlazoParametro.porcInteresTotal`; it does not re-derive avalúo from `PlazoHechuraAlhaja`. It is already functionally correct today. What *does* depend on the gold engine fix is the **accuracy of `montoPrestamo` itself** at contract-creation time — so amortización's numbers will only become trustworthy once #1 lands, even though no code change to `calcularAmortizacion()` is required.

---

## 6. Anti-Patterns to Avoid in This Integration

### Anti-Pattern 1: Re-deriving oro cells from a "clean" formula
**What could happen:** Reflexively "fixing" the gold engine by tuning the 3 global factors more precisely, or adding a 4th/5th factor, instead of accepting per-cell irregular data.
**Why it's wrong:** PROJECT.md explicitly confirms COCAE's table "no se deriva de una fórmula limpia" — any formula-based approach, however refined, will not match legacy amounts cajeros already know, which is the stated fidelity requirement.
**Do instead:** Import real values into `PlazoHechuraAlhaja.porcAumento` per cell; only use kilataje-linear scaling for the *price of gold changing*, never for hechura differentiation.

### Anti-Pattern 2: New entity for every "table-shaped" business rule
**What could happen:** Creating a `PlazoLeyPlata` entity mirroring `PlazoHechuraAlhaja` out of habit/symmetry with the oro model.
**Why it's wrong:** Silver has 2 discrete levels (925/725), already fit naturally as two columns on `PlazoParametro`. A new keyed entity adds a repository, mapper, controller endpoints, and a join for no material benefit — violates the codebase's own convention of using `BaseService`/`BaseRepository` only where CRUD-by-composite-key is actually needed.
**Do instead:** Add calculation logic reading existing `PlazoParametro` fields (§3.2).

### Anti-Pattern 3: Coupling `TurnoService` to `Contrato`/`MovimientoContrato` to "fix" the caja bug
**What could happen:** Injecting `MovimientoContratoRepository` into `TurnoService.cerrarTurno()` to compute a summary inline.
**Why it's wrong:** Breaks the existing module boundary (`.planning/codebase/ARCHITECTURE.md`'s Module Breakdown table keeps Turnos and Contratos/Avaluos as separate domains); makes `TurnoService` depend on the contract domain, inverting the natural dependency direction (contracts already depend on `Turno`, not the other way around).
**Do instead:** A separate `ReporteService` that reads both `Turno` and `MovimientoContrato`/`Contrato`, called independently by the frontend.

### Anti-Pattern 4: Trusting client-sent `avaluoReal` for plata like Varios
**What could happen:** Leaving plata on the same "free capture" path as Varios because it's the path of least resistance (both currently share `calcularPrestamoMaximo`'s fallback branch).
**Why it's wrong:** PROJECT.md and AVALUOS.md's business-rule discussion with Jorge treats plata as a metal with objective pricing (like oro), not a manually-appraised category (like electronics) — conflating them contradicts the "Reglas por tipo de pieza" requirement (Alhajas/Plata by metal formula, Varios by valuator judgment).
**Do instead:** Server-side `calcularAvaluoPlata()` as described in §3.2, mirroring the oro pattern of "the server, not the client, fixes the avalúo."

### Anti-Pattern 5: Deriving folio from `Contrato.id` indefinitely without flagging the coupling
**What could happen:** Treating the current `CTR-%06d` (id-based) folio as permanently sufficient.
**Why it's wrong:** It works today because there's a single sucursal, but ties the folio number space to the JPA identity generator — any future multi-sucursal folio series, manual folio correction, or id-gap-hiding requirement (e.g., avoid leaking contract volume via sequential public folios) will require decoupling. Not a blocker for this milestone (single-sucursal, per Constraints), but should be called out as a known limitation rather than silently assumed permanent.
**Do instead:** Note it in code comments/ADR; revisit only if multi-sucursal folio independence becomes a requirement.

---

## 7. Pitfalls Flagged for Deeper Phase-Specific Research

| Area | Pitfall | Why it needs its own research/verification pass |
|---|---|---|
| Oro 24-cell import | PROJECT.md itself says: "Falta confirmar si esa tabla es global o varía por plazo/tabla en COCAE (pendiente de captura adicional)." | This is a **business-data question, not an architecture question** — do not finalize the import mechanism (per-plazo vs global-then-copied) until that's confirmed with COCAE screenshots. |
| Plata calculation | No confirmed formula from Jorge/COCAE captures yet for how `ley` + `precioGramoPlata` combine (linear? does it need its own "aumento" margin analogous to oro's `porcAumento`, currently absent from `PlazoParametro`'s silver fields?). | The formula sketched in §3.2 is an architectural best-guess consistent with the existing schema shape (ley × precio_gramo), not a verified-against-COCAE formula. |
| Jasper PDF | `contrato.jrxml` field names/layout were not inspected in this pass (binary/XML report definition, out of scope for an architecture-only research pass). | Needed before implementing `ContratoReporteService` — confirm field bindings match `Contrato`/`PartidaContrato`/`Cliente` getters. |
| Reposición legacy discrepancy | The "bug conocido" pre-dates changeset 011's `MovimientoContrato.sancion/abonoCapital/semanasVencidas` columns and `cobrarReposicion()` method — it may already be structurally impossible to reproduce in the current code (there's no legacy double-booking path left), meaning the "fix" may really just be "build the first-ever caja report correctly," not "correct a divergence." | Confirm with the user/PM whether the originally-reported bug was observed against *current* code or against the older mock/simplified flow described in the stale `AVALUOS.md`. |

---

## Sources

Primary sources are the current repository state, read directly (not the stale planning docs, which are cited only to show drift):

- `prestamil-backend/src/main/java/com/ignis/prestamil/service/PlazoService.java`
- `prestamil-backend/src/main/java/com/ignis/prestamil/service/ContratoService.java`
- `prestamil-backend/src/main/java/com/ignis/prestamil/service/MovimientoContratoService.java`
- `prestamil-backend/src/main/java/com/ignis/prestamil/model/PlazoParametro.java`
- `prestamil-backend/src/main/java/com/ignis/prestamil/model/PlazoHechuraAlhaja.java` / `PlazoHechuraAlhajaId.java`
- `prestamil-backend/src/main/java/com/ignis/prestamil/model/Contrato.java` / `PartidaContrato.java` / `MovimientoContrato.java` / `TipoMovimiento.java`
- `prestamil-backend/src/main/java/com/ignis/prestamil/controller/ContratoController.java` / `MovimientoContratoController.java`
- `prestamil-backend/src/main/java/com/ignis/prestamil/request/ContratoRequest.java` / `PartidaContratoRequest.java`
- `prestamil-backend/src/main/java/com/ignis/prestamil/service/PrinterService.java`
- `prestamil-backend/src/main/resources/db/changelog/changes/001-initial-schema.sql`, `002-initial-data.sql`, `006-plazos-sucursal.sql`, `007-contratos.sql`, `011-oro-sancion-plata.sql`
- `prestamil-backend/src/main/resources/jasper/contrato.jrxml` (existence confirmed, contents not inspected)
- `prestamil-frontend/src/app/app-routing.module.ts`
- `prestamil-frontend/src/app/prestamil/core/services/` (directory listing — `movimiento.service.ts`, `plazo.service.ts`, `contrato.service.ts` present)
- `prestamil-frontend/src/app/prestamil/pages/avaluos/avaluo/avaluo.component.ts` (partial read)
- `.planning/PROJECT.md`, `.planning/codebase/ARCHITECTURE.md`, `.planning/codebase/AVALUOS.md`, `.planning/codebase/CONCERNS.md`, `.planning/codebase/STRUCTURE.md` (2026-05-22 snapshot — used to establish drift, not as ground truth for current state)

---
*Architecture research for: Prestamil v1.1 milestone integration*
*Researched: 2026-07-02*
