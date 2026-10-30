package com.ignis.prestamil.mapper;

import com.ignis.prestamil.model.Caja;
import com.ignis.prestamil.model.Denominacion;
import com.ignis.prestamil.model.TraspasoCaja;
import com.ignis.prestamil.model.TraspasoCajaDenominacion;
import com.ignis.prestamil.response.CajaResponse;
import com.ignis.prestamil.response.DenominacionResponse;
import com.ignis.prestamil.response.TraspasoCajaDenominacionResponse;
import com.ignis.prestamil.response.TraspasoCajaResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Cajas, denominaciones y traspasos (CAJ-01c). Solo entidad → respuesta: los traspasos los arma el
 * servicio a partir del request, porque las cajas y la fecha las pone el servidor.
 */
@Mapper(componentModel = "spring")
public interface CajaMapper {

    CajaResponse toCajaResponse(Caja caja);

    List<CajaResponse> toCajaResponses(List<Caja> cajas);

    DenominacionResponse toDenominacionResponse(Denominacion denominacion);

    List<DenominacionResponse> toDenominacionResponses(List<Denominacion> denominaciones);

    @Mapping(source = "cajaOrigen.id", target = "cajaOrigenId")
    @Mapping(source = "cajaOrigen.clave", target = "cajaOrigenClave")
    @Mapping(source = "cajaOrigen.nombre", target = "cajaOrigenNombre")
    @Mapping(source = "cajaDestino.id", target = "cajaDestinoId")
    @Mapping(source = "cajaDestino.clave", target = "cajaDestinoClave")
    @Mapping(source = "cajaDestino.nombre", target = "cajaDestinoNombre")
    @Mapping(source = "usuario.id", target = "usuarioId")
    @Mapping(source = "usuario.nombreUsuario", target = "nombreUsuario")
    @Mapping(source = "turno.id", target = "turnoId")
    @Mapping(source = "usuarioCancela.nombreUsuario", target = "nombreUsuarioCancela")
    TraspasoCajaResponse toTraspasoResponse(TraspasoCaja traspaso);

    List<TraspasoCajaResponse> toTraspasoResponses(List<TraspasoCaja> traspasos);

    @Mapping(source = "denominacion.id", target = "denominacionId")
    @Mapping(source = "denominacion.valor", target = "valor")
    TraspasoCajaDenominacionResponse toDesgloseResponse(TraspasoCajaDenominacion renglon);
}
