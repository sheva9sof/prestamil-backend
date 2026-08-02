# Módulo Avaluos — Diseño e Inventario

**Creado:** 2026-05-22
**Estado:** Mock frontend renombrado a componente real. Backend Fase A: completa.

---

## 1. Qué existe hoy y es reutilizable

### 1.1 Catálogos de prendas (backend ya funcional)

| Endpoint | Propósito | Uso en Avaluos |
|----------|-----------|----------------|
| `GET /api/prendas/tipos` | Lista de tipos de prenda | Selector de tipo en el formulario |
| `GET /api/prendas/subtipos/{idTipoPrenda}` | Atributos por tipo | Campos dinámicos de captura |
| `GET /api/prendas/valores/{idAtributo}` | Catálogo de ítems con clave/descripción/kilataje | Modal de búsqueda de prenda |

**Tipos de prenda en DB (`tipo_prenda`):**
| id | tipo |
|----|------|
| 1 | ALHAJA |
| 3 | VARIOS |
| 4 | PLATAS |
| 5 | AUTOS/MOTOS |

**Subtipos (`cat_subtipo_prenda`):**
| id_atributo | id_tipo_prenda | nombre_atributo |
|-------------|----------------|-----------------|
| 4 | 1 (ALHAJA) | Kilataje |
| 5 | 1 (ALHAJA) | Hechuras |
| 6 | 3 (VARIOS) | Estandar |
| 7 | 4 (PLATAS) | Tipo |
| 8 | 5 (AUTOS) | Tipo |
| 9 | 5 (AUTOS) | Marca |
| 10 | 5 (AUTOS) | Modelo |

**Valores (`cat_valor_prenda`):** campos relevantes para avaluos:
- `clave` — número de clave de pieza
- `descripcion` — descripción del ítem (ej. "AHOGADOR ORO 14K")
- `kilataje` — kilataje del oro (6-24)
- `contiene_piedad` — si tiene piedra preciosa

### 1.2 Tabla de precios de alhajas (backend ya funcional)

**`PlazoHechuraAlhaja`** — clave: `(id_plazo, sucursal_id, kilataje, hechura)`
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `precio_base` | DECIMAL(12,4) | Precio por gramo según kilataje/hechura |
| `porc_aumento` | DECIMAL(5,4) | % de margen sobre precio base |
| `precio_prestamo` | DECIMAL(12,4) | = precio_base × (1 + porc_aumento/100) |

Endpoint: `GET /api/plazos/{id}/alhajas?sucursalId=1`

### 1.3 Parámetros de plazo por tipo de prenda (backend ya funcional)

**`PlazoParametro`** — clave: `(plazo_id, tipo_prenda_id, sucursal_id)`

Campos relevantes para el cálculo:
| Campo | Descripción |
|-------|-------------|
| `porc_interes` | % interés por periodo |
| `porc_almacen` | % almacén |
| `porc_gastos_admin` | % gastos admin |
| `porc_prestamo_s_avaluo` | % del avalúo que se presta |
| `usa_avaluo_real` | Si usa el precio real del avalúo |
| `porc_prestamo_s_avaluo_real` | % de incremento para calcular el avalúo en contrato |
| `num_max_refrendos` | Máximo de refrendos permitidos |
| `dias_gracia_sin_interes` | Días de gracia |
| `dias_antes_pase_venta` | Días antes de pasar a venta |
| `importe_min_prestamo` | Monto mínimo de préstamo |

Endpoint: `GET /api/plazos/{id}/parametros/{tipoPrendaId}?sucursalId=1`

### 1.4 Clientes (backend ya funcional)

`GET /api/clientes` / `GET /api/clientes/search?q=`

**`Cliente`:** id, nombre, apellidoPaterno, apellidoMaterno, telefono, curp, rfc, activo, direccion

### 1.5 Identificaciones oficiales (catálogo existente)

`GET /api/catalogos/tipo/1` — tipo 1 = "Identificación oficial"

Valores en DB:
- Credencial de elector
- Pasaporte
- Licencia de manejo
- Cedula profesional
- Cartilla S.M.N

### 1.6 Parámetros del sistema relevantes

`GET /api/parametros-sistema` — campos que afectan el módulo:

| id | descripcion | valor |
|----|-------------|-------|
| 3 | ¿Tomar precio venta de la tabla? | 1 (bool) |
| 4 | Factor incremento precio venta alhajas | 3.00 |
| 5 | Factor incremento precio venta varios | 3.00 |
| 6 | Descuento venta público alhajas | 30.00 |
| 7 | Descuento venta público varios | 30.00 |
| 9 | Préstamo máximo por Contrato | 0 (sin límite) |
| 10 | ¿Imprimir código de barras? | 1 (bool) |
| 11 | ¿Imprime carta responsiva? | 1 (bool) |

---

## 2. Fórmulas de cálculo

### 2.1 ALHAJAS / PLATAS

```
precioBase      = PlazoHechuraAlhaja.precio_base      (por kilataje + hechura)
porcAumento     = PlazoHechuraAlhaja.porc_aumento
precioPrestamo  = precioBase × (1 + porcAumento / 100)
prestamo        = precioPrestamo × peso_gramos
avaluoContrato  = prestamo × (1 + porcIncrementoAvaluo / 100)
                  donde porcIncrementoAvaluo = PlazoParametro.porc_prestamo_s_avaluo_real
```

Si `usa_avaluo_real = false` → `avaluoContrato = prestamo` (sin incremento).

### 2.2 VARIOS

```
prestamo        = captura manual del valuador
avaluoContrato  = prestamo × (1 + porcIncrementoAvaluo / 100)
                  donde porcIncrementoAvaluo = PlazoParametro.porc_prestamo_s_avaluo_real
```

### 2.3 Fecha de vencimiento

```
fechaVencimiento = fechaApertura + (diasPorPeriodo × numeroPeriodos)
```

---

## 3. Entidades implementadas ✅ (Fase A — 260522-h4a)

### 3.1 `Contrato` — cabecera del contrato de empeño ✅ IMPLEMENTADA

```sql
CREATE TABLE contrato (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  folio               VARCHAR(20) NOT NULL UNIQUE,         -- ej. CTR-000001
  id_cliente          INT NOT NULL,
  id_turno            BIGINT NOT NULL,
  id_sucursal         INT NOT NULL,
  id_plazo            BIGINT NOT NULL,
  id_usuario          INT NOT NULL,                        -- cajero que lo registró
  id_beneficiario     INT,                                 -- cliente opcional
  nombre_beneficiario VARCHAR(200),
  tipo_identificacion VARCHAR(60),
  num_identificacion  VARCHAR(30),
  fecha_apertura      DATETIME NOT NULL,
  fecha_vencimiento   DATE NOT NULL,
  monto_prestamo      DECIMAL(18,2) NOT NULL,
  monto_avaluo        DECIMAL(18,2) NOT NULL,
  estatus             ENUM('VIGENTE','VENCIDO','DESEMPEÑADO','EN_VENTA') NOT NULL DEFAULT 'VIGENTE',
  num_refrendos       INT NOT NULL DEFAULT 0,
  creado_en           DATETIME NOT NULL,
  actualizado_en      DATETIME NOT NULL,

  CONSTRAINT fk_contrato_cliente  FOREIGN KEY (id_cliente) REFERENCES clientes(id),
  CONSTRAINT fk_contrato_turno    FOREIGN KEY (id_turno)   REFERENCES turnos(id_turno),
  CONSTRAINT fk_contrato_sucursal FOREIGN KEY (id_sucursal) REFERENCES sucursal(id),
  CONSTRAINT fk_contrato_plazo    FOREIGN KEY (id_plazo)   REFERENCES plazo(id),
  CONSTRAINT fk_contrato_usuario  FOREIGN KEY (id_usuario) REFERENCES usuarios(id)
);
```

### 3.2 `PartidaContrato` — líneas de prenda dentro del contrato ✅ IMPLEMENTADA

```sql
CREATE TABLE partida_contrato (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  id_contrato         BIGINT NOT NULL,
  num_partida         INT NOT NULL,                        -- 1, 2, 3...
  id_tipo_prenda      INT NOT NULL,
  id_valor_prenda     INT,                                 -- FK cat_valor_prenda (nullable para Varios)
  clave_prenda        VARCHAR(20),
  descripcion         VARCHAR(200) NOT NULL,
  cantidad            INT NOT NULL DEFAULT 1,
  peso_gramos         DECIMAL(10,4),                       -- nulo para Varios
  kilataje            INT,                                  -- nulo para Varios
  hechura             VARCHAR(5),                           -- F/N/E, nulo para Varios
  precio_x_gramo      DECIMAL(12,4),
  avaluo_real         DECIMAL(18,2) NOT NULL,
  avaluo_contrato     DECIMAL(18,2) NOT NULL,
  monto_prestamo      DECIMAL(18,2) NOT NULL,
  subtipo             VARCHAR(50),                         -- para Varios: Celular, Laptop...
  marca               VARCHAR(80),
  modelo              VARCHAR(80),
  serie_imei          VARCHAR(60),
  estado_fisico       VARCHAR(20),                         -- Bueno/Regular/Malo

  CONSTRAINT fk_partida_contrato  FOREIGN KEY (id_contrato)   REFERENCES contrato(id),
  CONSTRAINT fk_partida_tipo      FOREIGN KEY (id_tipo_prenda) REFERENCES tipo_prenda(id),
  CONSTRAINT fk_partida_valor     FOREIGN KEY (id_valor_prenda) REFERENCES cat_valor_prenda(id_valor_atributo)
);
```

### 3.3 `MovimientoContrato` — historial de pagos / refrendos / finiquitos ✅ IMPLEMENTADA

```sql
CREATE TABLE movimiento_contrato (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  id_contrato     BIGINT NOT NULL,
  id_turno        BIGINT NOT NULL,
  id_usuario      INT NOT NULL,
  tipo            ENUM('REFRENDO','FINIQUITO','ABONO') NOT NULL,
  monto           DECIMAL(18,2) NOT NULL,
  interes         DECIMAL(18,2),
  fecha           DATETIME NOT NULL,
  observaciones   VARCHAR(300),

  CONSTRAINT fk_mov_contrato FOREIGN KEY (id_contrato) REFERENCES contrato(id),
  CONSTRAINT fk_mov_turno    FOREIGN KEY (id_turno)    REFERENCES turnos(id_turno),
  CONSTRAINT fk_mov_usuario  FOREIGN KEY (id_usuario)  REFERENCES usuarios(id)
);
```

---

## 4. API endpoints — Contratos

### Implementados ✅ (Fase A — 260522-h4a)

| Método | Path | Descripción |
|--------|------|-------------|
| `POST` | `/api/contratos` | ✅ Crear contrato con sus partidas |
| `GET` | `/api/contratos/{id}` | ✅ Detalle de contrato + partidas |
| `GET` | `/api/contratos/folio/{folio}` | ✅ Buscar por folio |
| `GET` | `/api/contratos/cliente/{clienteId}` | ✅ Historial de contratos del cliente |
| `GET` | `/api/contratos/vencidos` | ✅ Contratos vencidos (para gestión) |

### Pendientes (Fase B/C)

| Método | Path | Descripción |
|--------|------|-------------|
| `GET` | `/api/contratos` | Listar contratos (paginado + filtros) |
| `PUT` | `/api/contratos/{id}/refrendo` | Registrar refrendo |
| `PUT` | `/api/contratos/{id}/finiquito` | Registrar finiquito (desempeño) |
| `GET` | `/api/contratos/{id}/pdf` | Generar PDF del contrato |

### Cálculo (opcional — puede ser local en frontend)

| Método | Path | Descripción |
|--------|------|-------------|
| `POST` | `/api/contratos/calcular` | Calcula montos sin persistir (dry-run) |

---

## 5. APIs que el componente Avaluos consume (actualizado 2026-05-22)

El componente `avaluo.component.ts` ya no es mock — consume APIs reales:

| Servicio | Método | Estado |
|----------|--------|--------|
| `AuthService.getUser()` | — | ✅ Real (sesión) |
| `PlazoService.getAll()` | `GET /api/plazos` | ✅ Real |
| `PlazoService.getParametro()` | `GET /api/plazos/{id}/parametros/{tipoPrendaId}` | ✅ Real |
| `PlazoService.getTablaAlhajas()` | `GET /api/plazos/{id}/alhajas` | ✅ Real |
| `ClienteService.search()` | `GET /api/clientes/search?q=` | ✅ Real (conectado) |
| `PrendaService.getValoresPrenda()` | `GET /api/prendas/valores/{idAtributo}` | ✅ Real (conectado) |
| `ContratoService.crearContrato()` | `POST /api/contratos` | ✅ Real (conectado) |
| `ContratoService.getContratosPorCliente()` | `GET /api/contratos/cliente/{id}` | ✅ Real (conectado) |

---

## 6. Flujo de implementación sugerido (fases)

### Fase A — Backend core (prioridad alta)
1. Liquibase changeset 007: tablas `contrato`, `partida_contrato`, `movimiento_contrato`
2. Entidades JPA: `Contrato`, `PartidaContrato`, `MovimientoContrato`
3. Repositorios y servicios: `ContratoService`, `PartidaService`
4. `ContratoController`: `POST /api/contratos`, `GET /api/contratos/{id}`, `GET /api/clientes/{id}/contratos`
5. Lógica de cálculo en `ContratoService`: fórmulas alhaja y varios usando datos de `PlazoHechuraAlhaja` y `PlazoParametro`

### Fase B — Frontend conectado
1. `contrato.service.ts` / `contrato.model.ts`
2. Reemplazar mocks de clientes y prendas con llamadas reales a APIs existentes
3. Conectar `POST /api/contratos` al botón "Confirmar y generar"
4. Mostrar contratos anteriores del cliente en modal "Ver vencimientos"

### Fase C — PDF y operaciones
1. Template JasperReports para contrato (base ya existe en `contrato.jrxml`)
2. `GET /api/contratos/{id}/pdf` — genera PDF
3. `PUT /api/contratos/{id}/refrendo` + UI de refrendos
4. `PUT /api/contratos/{id}/finiquito` + UI de finiquito

---

## 7. Decisiones de negocio pendientes

Estas preguntas bloquean o modifican el diseño antes de implementar el backend:

1. **Libre avalúo:** ¿Puede el valuador sobreescribir el precio calculado por el sistema (precio_x_gramo × peso)?
2. **Tipos mixtos:** ¿Un contrato puede tener partidas de tipos distintos (ej: alhaja + celular)?
3. **Beneficiario:** ¿El campo es obligatorio o siempre opcional?
4. **Refrendos/Finiquitos:** ¿Entran por la misma pantalla de Avaluos o pantalla separada?
5. **Precios de varios:** ¿Existe tabla de referencia para electrónicos o el valuador decide a criterio propio?
6. **Folio:** ¿El folio del contrato lo genera el sistema automáticamente o lo captura el cajero?
7. **Reposición de contrato:** `PlazoParametro` tiene campos `cobrar_reposicion_contrato`, `porc_reposicion`, `monto_reposicion` — ¿se cobra reposición cuando el cliente pierde el contrato físico?
8. **Sanciones:** `aplicar_sancion_por_periodo` — ¿la sanción por pago tardío se aplica en esta versión?

---

## 8. Estado del componente avaluo (2026-05-22)

**Archivo:** `prestamil-frontend/src/app/prestamil/pages/avaluos/avaluo/avaluo.component.ts`
**Ruta:** `/avaluos`

| Funcionalidad | Estado |
|---------------|--------|
| Selección de cliente (modal filtrable) | ✅ Real (`ClienteService.search()`) |
| Selección de plazo | ✅ Real (`PlazoService.getAll()`) |
| Formulario ALHAJAS con cálculos | ✅ Real (`PlazoHechuraAlhajaService`) |
| Formulario VARIOS con préstamo manual | ✅ Funcional |
| Modal catálogo de prendas | ✅ Real (`PrendaService.getValoresPrenda()`) |
| Modal confirmación de contrato | ✅ Real (persiste via `POST /api/contratos`) |
| Modal vencimientos del cliente | ✅ Real (`GET /api/contratos/cliente/{id}`) |
| Tabla de partidas con avalúo contrato | ✅ Funcional local |
| Validaciones de flujo | ✅ Funcional |
| Conexión a `POST /api/contratos` | ✅ Real (conectado — Fase A completa) |
| PDF del contrato | ❌ Pendiente (Fase C) |
| Refrendos / Finiquitos | ❌ Pendiente — módulo separado (Fase C) |
