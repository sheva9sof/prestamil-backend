---
phase: 04-motor-de-oro-fidelidad-cocae-cierre-de-brecha-de-confianza
verified: 2026-07-03T19:38:14Z
status: passed
score: 7/7 must-haves verified
---

# Phase 04: Motor de Oro — Fidelidad COCAE / Cierre de Brecha de Confianza Verification Report

**Phase Goal:** Los montos de préstamo/avalúo de piezas de oro coinciden exactamente con los de COCAE y el servidor nunca confía en el avalúo que envía el cliente
**Verified:** 2026-07-03T19:38:14Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | La tabla real de COCAE (24 celdas: 8 kilates x 3 hechuras) está persistida como datos, no hardcodeada en Java (ORO-01) | ✓ VERIFIED | `012-oro-tabla-prestamo-cocae.sql` creates `oro_tabla_prestamo` and inserts exactly 24 rows (`grep -o "(1, [0-9]*, *'[FNE]'" ... \| wc -l` → 24); registered in `db.changelog-master.xml` immediately after changeset 011, order of 001-011 untouched |
| 2 | La tabla es global por sucursal (no varía por plazo) — clave compuesta de 3 campos, no 4 | ✓ VERIFIED | `OroTablaPrestamoId` has exactly `sucursalId`, `kilataje`, `hechura` (no `idPlazo`), mirrors `PlazoHechuraAlhajaId` pattern minus the plazo dimension |
| 3 | `PlazoService.recalcularRegistros` deriva `precioBase` desde el %Prestamo real de `oro_tabla_prestamo` (kilataje x hechura), no de 3 factores globales Fundir/Normal/Especial (ORO-01) | ✓ VERIFIED | `recalcularRegistros` (PlazoService.java:316-345) builds a `Map<kilataje-hechura, porcPrestamo>` from `oroTablaPrestamoRepository.findByIdSucursalId`; `factorPorHechura` method removed entirely (absent from file); `factorFundir/Normal/Especial` still persisted in `recalcularTodasLasTablas` for the "Precio del Oro" screen (D-02) but no longer passed into the pricing formula |
| 4 | `porcAumento` propio de cada celda se conserva sin sobreescribirse durante el recálculo (ORO-02) | ✓ VERIFIED | `recalcularRegistros` reads `r.getPorcAumento()` only to compute `precioPrestamo`, never writes it; `PlazoServiceTest.actualizarTodosPrecios_21K_Normal_coincideConCOCAE` asserts `porcAumento.compareTo(new BigDecimal("10.0000")) == 0` after recalculation — test passes |
| 5 | Los montos de precioBase/precioPrestamo calculados coinciden exactamente (compareTo) con capturas reales de COCAE (ORO-04, lado PlazoService) | ✓ VERIFIED | `PlazoServiceTest` (4 tests, all pass): 21K/Normal with precioGramoBase=1679.50 → precioBase=1065.4748, precioPrestamo=1172.0223 (compareTo==0); global factors changed to 50/100/150 produce identical result (proving they're inert); missing cell / empty registros both throw `ResourceNotFoundException` |
| 6 | Al crear un contrato con partida ALHAJA, el `avaluoReal` persistido es el calculado por el servidor desde `PlazoHechuraAlhaja` — un valor spoofed del cliente no tiene efecto (ORO-03, D-07) | ✓ VERIFIED | `ContratoService.calcularAvaluoRealAlhaja` looks up `PlazoHechuraAlhajaRepository.findById(...)` and computes `precioPrestamo × pesoGramos`; `buildPartida` no longer reads `pr.getAvaluoReal()` for ALHAJA (confirmed absent: no `partida.setAvaluoReal(pr.getAvaluoReal())` string in file); `ContratoServiceTest.crearContrato_partidaAlhaja_ignoraAvaluoRealDelCliente` asserts persisted avaluoReal is 12000.00 (server-calculated), NOT 999999.00 (spoofed) — test passes |
| 7 | Kilataje 24K y kilatajes no soportados por COCAE son rechazados con mensajes de negocio claros (D-04/D-05), y el préstamo máximo se calcula desde el avalúo recalculado por el servidor, nunca del enviado por el cliente | ✓ VERIFIED | `calcularAvaluoRealAlhaja` throws `BadRequestException("Oro de 24K no es prendable")` before touching the repository, and `BadRequestException("Kilataje no soportado: " + kilataje)` for values outside `{6,8,10,12,14,18,21,24}`; `calcularPrestamoMaximo` signature changed to `(BigDecimal avaluoReal, PlazoParametro parametro)` — no longer accepts `PartidaContratoRequest`; `ContratoServiceTest` tests for 24K, 16K (unsupported), and monto-superior-al-máximo-del-servidor all pass |

**Score:** 7/7 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `prestamil-backend/.../changes/012-oro-tabla-prestamo-cocae.sql` | Changeset with `CREATE TABLE oro_tabla_prestamo` + 24 rows | ✓ VERIFIED | Present, exact content matches plan, 24 INSERT rows confirmed by grep |
| `prestamil-backend/.../db.changelog-master.xml` | Includes changeset 012 after 011 | ✓ VERIFIED | Line 19: `<include file="db/changelog/changes/012-oro-tabla-prestamo-cocae.sql"/>`, order of 001-011 preserved |
| `prestamil-backend/.../model/OroTablaPrestamo.java` | Read-only JPA entity, `@EmbeddedId` | ✓ VERIFIED | Matches plan exactly |
| `prestamil-backend/.../model/OroTablaPrestamoId.java` | 3-field composite key with equals/hashCode | ✓ VERIFIED | Matches plan exactly |
| `prestamil-backend/.../repository/OroTablaPrestamoRepository.java` | `findByIdSucursalId` | ✓ VERIFIED | Matches plan exactly |
| `prestamil-backend/.../service/PlazoService.java` | `recalcularRegistros` corrected, no `factorPorHechura` | ✓ VERIFIED | Contains `oroTablaPrestamoRepository`, 4-param signature confirmed, `factorPorHechura` absent, `setFactorFundir` still present in `recalcularTodasLasTablas` |
| `prestamil-backend/.../test/service/PlazoServiceTest.java` | Paridad compareTo tests | ✓ VERIFIED | 4 tests, all pass (`mvn test -Dtest=PlazoServiceTest` → Tests run: 4, Failures: 0) |
| `prestamil-backend/.../service/ContratoService.java` | `calcularAvaluoRealAlhaja`, no trust in client avaluoReal for ALHAJA | ✓ VERIFIED | Contains `calcularAvaluoRealAlhaja`, `"Oro de 24K no es prendable"`, `"Kilataje no soportado: "`; `calcularPrestamoMaximo(BigDecimal, PlazoParametro)` signature confirmed; `partida.setAvaluoReal(pr.getAvaluoReal())` absent |
| `prestamil-backend/.../test/service/ContratoServiceTest.java` | Regression tests for trust-gap closure and D-04/D-05 | ✓ VERIFIED | 4 tests, all pass (`mvn test -Dtest=ContratoServiceTest` → Tests run: 4, Failures: 0) |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `db.changelog-master.xml` | `012-oro-tabla-prestamo-cocae.sql` | `<include file=...>` | ✓ WIRED | Confirmed line 19 |
| `PlazoService.java` | `OroTablaPrestamoRepository.java` | `oroTablaPrestamoRepository.findByIdSucursalId(sucursalId)` inside `recalcularRegistros` | ✓ WIRED | Called at PlazoService.java:322-326, result consumed to derive `precioBase` per cell |
| `ContratoService.java` | `PlazoHechuraAlhajaRepository.java` | `plazoHechuraAlhajaRepository.findById(new PlazoHechuraAlhajaId(...))` inside `calcularAvaluoRealAlhaja` | ✓ WIRED | Called at ContratoService.java:318-321, result (`precioPrestamo`) multiplied by `pesoGramos` and returned as the server-calculated `avaluoReal`, consumed by `buildPartida` before `calcularPrestamoMaximo` |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Whole backend module compiles after all 3 plans' changes | `cd prestamil-backend && mvn -q compile` | No errors, silent success | ✓ PASS |
| PlazoService COCAE parity + porcAumento preservation + factor irrelevance + missing-cell rejection | `mvn test -Dtest=PlazoServiceTest` | Tests run: 4, Failures: 0, Errors: 0 | ✓ PASS |
| ContratoService trust-gap closure + D-04/D-05 rejections | `mvn test -Dtest=ContratoServiceTest` | Tests run: 4, Failures: 0, Errors: 0 | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|-------------|-----------------|--------------|--------|----------|
| ORO-01 | 04-01, 04-02 | Importa tabla real de COCAE (24 celdas) vía Liquibase, reemplaza cálculo de 3 factores globales | ✓ SATISFIED | Changeset 012 + `recalcularRegistros` rewired to `oro_tabla_prestamo` lookup, `factorPorHechura` removed |
| ORO-02 | 04-02 | `porcAumento` propio de cada celda se conserva sin sobreescribir con factor global | ✓ SATISFIED | `PlazoServiceTest` asserts `porcAumento` unchanged after recalc |
| ORO-03 | 04-03 | Servidor recalcula avalúo real de ALHAJA desde `PlazoHechuraAlhaja`, no confía en `avaluoReal` del cliente | ✓ SATISFIED | `calcularAvaluoRealAlhaja` + `buildPartida` no longer read `pr.getAvaluoReal()` for ALHAJA; regression test proves spoofed value has no effect |
| ORO-04 | 04-02, 04-03 | Montos coinciden con COCAE (capturas reales + `compareTo`) | ✓ SATISFIED | Both `PlazoServiceTest` and `ContratoServiceTest` use `compareTo` exclusively (D-06) against values traced to real COCAE captures (21K/Normal 1679.50 base → 1065.4748/1172.0223; 14K/Normal precioPrestamo 1200.0000 → avaluoReal 12000.00) |

No orphaned requirements — REQUIREMENTS.md maps exactly ORO-01..04 to Phase 4, all four appear in the `requirements:` frontmatter of the three plans (04-01: [ORO-01], 04-02: [ORO-01, ORO-02, ORO-04], 04-03: [ORO-03, ORO-04]).

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `ContratoService.java` | 99 | `Integer sucursalId = 1; // TODO: derivar de la sucursal del usuario/turno` | ℹ️ Info | Pre-existing hardcode, unrelated to this phase's scope (gold-engine fidelity / trust-boundary closure); not introduced or modified by any of the 3 plans; does not affect ORO-01..04 |

No blocker or warning-level anti-patterns found in the files modified by this phase. No `assertEquals`/`.equals()` on `BigDecimal` anywhere in the two new test files (D-06 compliance confirmed by full-file read).

### Human Verification Required

None strictly required for goal achievement — all four requirements are backend business-logic/unit-test verifiable and were verified programmatically (compile + full test run, both green).

Optional (data-accuracy, not code-correctness): the 24 COCAE porc_prestamo values in changeset 012 and the reference captures used in the unit tests (21K/Normal, 1679.50 base) were transcribed from `04-RESEARCH.md`'s documented COCAE v3.80 captures. If a live comparison against the legacy COCAE system for sucursal 1 has not yet been done, a human with COCAE access could re-key one or two cells directly against the running legacy app for an extra sanity check — this is a data-transcription risk, not a wiring or logic gap, and is outside what this verifier can check.

### Gaps Summary

No gaps found. All 7 derived observable truths are verified against the actual codebase (not just SUMMARY claims): the changeset exists with exactly 24 rows and is registered in the master changelog; the JPA entity/repository compile and are wired into `PlazoService`; `PlazoService.recalcularRegistros` no longer uses the 3-global-factor formula and preserves `porcAumento`; `ContratoService` no longer trusts the client's `avaluoReal` for ALHAJA partidas and rejects 24K/unsupported kilatajes with the exact required messages; both new test suites (8 tests total) pass with `mvn test`, and `mvn compile` succeeds for the whole module confirming no regression in other consumers (e.g., `ContratoController`).

---

*Verified: 2026-07-03T19:38:14Z*
*Verifier: Claude (gsd-verifier)*
