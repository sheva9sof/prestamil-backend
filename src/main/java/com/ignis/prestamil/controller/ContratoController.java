package com.ignis.prestamil.controller;

import com.ignis.prestamil.model.BuscarContratoPor;
import com.ignis.prestamil.model.FiltroEstatusOperacion;
import com.ignis.prestamil.response.ContratoOperacionDetalleResponse;
import com.ignis.prestamil.response.ContratoOperacionResponse;
import com.ignis.prestamil.response.ContratoResponse;
import com.ignis.prestamil.response.PageResponse;
import com.ignis.prestamil.response.PartidaRecalculadaResponse;
import com.ignis.prestamil.response.VencimientoResponse;
import com.ignis.prestamil.request.CancelarMovimientoRequest;
import com.ignis.prestamil.request.ContratoRequest;
import com.ignis.prestamil.request.RecalcularPartidasRequest;
import com.ignis.prestamil.response.MovimientoResponse;
import com.ignis.prestamil.service.CartaResponsivaService;
import com.ignis.prestamil.service.ContratoOperacionService;
import com.ignis.prestamil.service.ContratoService;
import com.ignis.prestamil.service.ContratoPdfService;
import com.ignis.prestamil.service.MovimientoContratoService;
import com.ignis.prestamil.service.TicketBolsaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contratos")
public class ContratoController {

    private final ContratoService contratoService;
    private final ContratoPdfService contratoPdfService;
    private final ContratoOperacionService contratoOperacionService;
    private final TicketBolsaService ticketBolsaService;
    private final CartaResponsivaService cartaResponsivaService;
    private final MovimientoContratoService movimientoContratoService;

    public ContratoController(ContratoService contratoService, ContratoPdfService contratoPdfService,
                              ContratoOperacionService contratoOperacionService,
                              TicketBolsaService ticketBolsaService,
                              CartaResponsivaService cartaResponsivaService,
                              MovimientoContratoService movimientoContratoService) {
        this.contratoService = contratoService;
        this.contratoPdfService = contratoPdfService;
        this.contratoOperacionService = contratoOperacionService;
        this.ticketBolsaService = ticketBolsaService;
        this.cartaResponsivaService = cartaResponsivaService;
        this.movimientoContratoService = movimientoContratoService;
    }

    /**
     * Registra un nuevo contrato de empeño.
     * POST /api/contratos
     */
    @PostMapping
    public ResponseEntity<ContratoResponse> crear(
            @Valid @RequestBody ContratoRequest request,
            Authentication authentication) {
        ContratoResponse response = contratoService.crearContrato(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Recalcula las partidas de un avalúo con los parámetros de un plazo nuevo (D-06).
     * Se invoca cada vez que el valuador cambia de plazo en Avalúos para que ningún valor
     * del plazo anterior quede "pegado" a la partida. El cuerpo contiene el idPlazo nuevo y
     * las partidas capturadas; la respuesta trae precio por gramo, avalúo real, préstamo
     * máximo, préstamo final (clamped y redondeado a $5 para alhajas) y avalúo de contrato.
     * POST /api/contratos/recalcular-partidas
     */
    @PostMapping("/recalcular-partidas")
    public ResponseEntity<List<PartidaRecalculadaResponse>> recalcularPartidas(
            @Valid @RequestBody RecalcularPartidasRequest request) {
        return ResponseEntity.ok(contratoService.recalcularPartidas(request));
    }

    /**
     * Obtiene un contrato por su ID.
     * GET /api/contratos/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ContratoResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(contratoService.getById(id));
    }

    /**
     * Obtiene un contrato por su folio.
     * GET /api/contratos/folio/{folio}
     */
    @GetMapping("/folio/{folio}")
    public ResponseEntity<ContratoResponse> findByFolio(@PathVariable String folio) {
        return ResponseEntity.ok(contratoService.getByFolio(folio));
    }

    /**
     * Lista contratos de un cliente ordenados del más reciente al más antiguo.
     * GET /api/clientes/{clienteId}/contratos
     */
    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<ContratoResponse>> findByCliente(@PathVariable Integer clienteId) {
        return ResponseEntity.ok(contratoService.getContratosPorCliente(clienteId));
    }

    /**
     * Lista contratos vencidos para gestión de recuperación.
     * GET /api/contratos/vencidos
     */
    @GetMapping("/vencidos")
    public ResponseEntity<List<ContratoResponse>> findVencidos() {
        return ResponseEntity.ok(contratoService.getContratosVencidos());
    }

    /**
     * Listado de la pantalla de Finiquitos y Refrendos con búsqueda y filtros, paginado desde 0.
     * GET /api/contratos/operacion?q=&buscarPor=&ramo=&estatus=&page=&size=
     */
    @GetMapping("/operacion")
    public ResponseEntity<PageResponse<ContratoOperacionResponse>> buscarOperacion(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "CONTRATO") BuscarContratoPor buscarPor,
            @RequestParam(required = false) Integer ramo,
            @RequestParam(defaultValue = "TODOS") FiltroEstatusOperacion estatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(contratoOperacionService.buscar(q, buscarPor, ramo, estatus, page, size));
    }

    /**
     * Detalle de un contrato con las acciones disponibles hoy (matriz RN-16).
     * GET /api/contratos/{id}/operacion
     */
    @GetMapping("/{id}/operacion")
    public ResponseEntity<ContratoOperacionDetalleResponse> detalleOperacion(@PathVariable Long id) {
        return ResponseEntity.ok(contratoOperacionService.detalle(id));
    }

    /**
     * Tabla de amortización (vencimientos por periodo) calculada al vuelo.
     * GET /api/contratos/{id}/amortizacion
     */
    @GetMapping("/{id}/amortizacion")
    public ResponseEntity<List<VencimientoResponse>> amortizacion(@PathVariable Long id) {
        return ResponseEntity.ok(contratoService.calcularAmortizacion(id));
    }

    /**
     * Genera el PDF del contrato de mutuo (para visualización/impresión en el sistema).
     * GET /api/contratos/{id}/pdf
     */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] pdf = contratoPdfService.generarPdf(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=contrato-" + id + ".pdf");
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    /**
     * PDF del contrato para una reposición/reimpresión (C-02). Solo entrega el PDF si existe un
     * movimiento RE no cancelado del día; si no, responde 409 y el usuario debe cobrar la reposición
     * primero. El endpoint {@code /pdf} genérico no toca esta validación: lo sigue usando la creación.
     * GET /api/contratos/{id}/pdf-reposicion
     */
    @GetMapping("/{id}/pdf-reposicion")
    public ResponseEntity<byte[]> pdfReposicion(@PathVariable Long id) {
        byte[] pdf = contratoPdfService.generarPdfParaReposicion(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=contrato-" + id + "-reposicion.pdf");
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    /**
     * Ticket de bolsa del contrato (D-10): PDF angosto para la impresora térmica. Se imprime al generar
     * el contrato y se reimprime desde la consulta de partidas.
     * GET /api/contratos/{id}/ticket-bolsa
     */
    @GetMapping("/{id}/ticket-bolsa")
    public ResponseEntity<byte[]> ticketBolsa(@PathVariable Long id) {
        byte[] pdf = ticketBolsaService.generarPdf(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=ticket-bolsa-" + id + ".pdf");
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    /**
     * Cancela un contrato recién creado (C-06, D-11, RN-26). En una sola transacción pone el contrato
     * en CANCELADO con saldo 0, marca todas sus partidas como CAN, cancela el movimiento EMP (sin
     * borrarlo), registra una ENTRADA de caja por el monto del préstamo ligada al EMP y escribe en
     * bitácora. Las reposiciones (RE) del mismo día no se revierten y no cuentan como "último
     * movimiento" (C-03). Devuelve el EMP cancelado — el frontend usa su id para pedir el
     * comprobante "CANCELACIÓN DE CONTRATO" en {@code GET /api/movimientos/{id}/ticket-cancelacion}.
     *
     * <p>Rechazos con mensaje específico: motivo inválido, rol no permitido (403), contrato ya
     * cancelado, sin EMP, con movimientos de cobro vigentes, de otro día, con el turno ya cerrado.
     * POST /api/contratos/{id}/cancelar</p>
     */
    @PostMapping("/{id}/cancelar")
    public ResponseEntity<MovimientoResponse> cancelarContrato(
            @PathVariable Long id,
            @Valid @RequestBody CancelarMovimientoRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
                movimientoContratoService.cancelarContrato(id, request, authentication.getName()));
    }

    /**
     * Carta responsiva del contrato (D-12), tamaño carta; se imprime entre el contrato y el ticket de bolsa.
     * GET /api/contratos/{id}/carta-responsiva
     */
    @GetMapping("/{id}/carta-responsiva")
    public ResponseEntity<byte[]> cartaResponsiva(@PathVariable Long id) {
        byte[] pdf = cartaResponsivaService.generarPdf(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=carta-responsiva-" + id + ".pdf");
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }
}
