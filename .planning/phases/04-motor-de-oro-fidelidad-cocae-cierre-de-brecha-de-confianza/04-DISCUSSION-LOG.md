# Phase 4: Motor de Oro — Fidelidad COCAE + Cierre de Brecha de Confianza - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-07-03
**Phase:** 4-motor-de-oro-fidelidad-cocae-cierre-de-brecha-de-confianza
**Areas discussed:** Edición de la tabla de 24 celdas, Manejo de kilataje 24K, Tolerancia de paridad con COCAE, Discrepancia servidor vs. pantalla del cajero, Contratos ya abiertos, Kilataje no soportado

---

## Edición de la tabla de 24 celdas

| Option | Description | Selected |
|--------|-------------|----------|
| Solo vía Liquibase | Solo un developer las cambia con un changeset nuevo si el negocio cambia sus reglas de margen. | ✓ |
| Editable desde UI en este phase | Agregar una pantalla tipo COCAE donde el admin edite las 24 celdas directamente. | |
| Editable, pero en phase futuro | Se importa fija ahora; queda anotado como backlog para un phase de administración futuro. | |

**User's choice:** Solo vía Liquibase (Recomendado)
**Notes:** La pantalla "Precio del Oro" existente se mantiene solo para precio del gramo y factores de hechura de referencia.

---

## Manejo de kilataje 24K (préstamo $0)

| Option | Description | Selected |
|--------|-------------|----------|
| Bloquear con mensaje claro | BadRequestException "Oro de 24K no es prendable". | ✓ |
| Dejar que importe mínimo la rechace | Sin lógica especial; el préstamo $0 falla naturalmente la regla de importe mínimo. | |

**User's choice:** Bloquear con mensaje claro (Recomendado)

---

## Tolerancia de paridad con COCAE

| Option | Description | Selected |
|--------|-------------|----------|
| Exacta al centavo | compareTo() en pruebas contra capturas reales; documentar contrato de redondeo. | ✓ |
| Tolerancia pequeña aceptable | Permitir diferencia de unos centavos si el redondeo interno de COCAE no se puede replicar exactamente. | |

**User's choice:** Exacta al centavo (Recomendado)

---

## Discrepancia servidor vs. pantalla del cajero

| Option | Description | Selected |
|--------|-------------|----------|
| Usar valor del servidor sin fricción | El servidor es la fuente de verdad; no se bloquea al cajero. | ✓ |
| Mostrar advertencia antes de confirmar | Requiere cambios de frontend, fuera del alcance backend de este phase. | |

**User's choice:** Usar valor del servidor sin fricción (Recomendado)

---

## Contratos ya abiertos (VIGENTE) con la fórmula anterior

| Option | Description | Selected |
|--------|-------------|----------|
| No se tocan | Montos ya snapshoteados en PartidaContrato al crear el contrato; es un acuerdo ya aceptado por el cliente. | ✓ |
| Recalcular retroactivamente | Actualizar avaluoContrato/montoPrestamo de contratos existentes. | |

**User's choice:** No se tocan (Recomendado)

---

## Kilataje no soportado (fuera de 6/8/10/12/14/18/21/24K)

| Option | Description | Selected |
|--------|-------------|----------|
| Rechazar con error claro | BadRequestException "Kilataje no soportado: {valor}". | ✓ |
| Interpolar entre kilates cercanos | Calcular un valor aproximado — COCAE mismo no lo hace. | |

**User's choice:** Rechazar con error claro (Recomendado)

---

## Claude's Discretion

- Formato exacto del changeset Liquibase (número de secuencia, nombre de tabla nueva vs. reutilizar `plazo_hechura_alhaja`)
- Formato de documentación del contrato de redondeo (Javadoc, README interno, o test dedicado)
- Texto exacto de los mensajes de error para kilataje 24K y kilataje no soportado

## Deferred Ideas

- UI de administración para editar la tabla de 24 celdas directamente desde Prestamil (espejo de COCAE) — futuro phase
