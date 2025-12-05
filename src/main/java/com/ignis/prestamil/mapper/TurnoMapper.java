package com.ignis.prestamil.mapper;

import com.ignis.prestamil.model.Turno;
import com.ignis.prestamil.response.TurnoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TurnoMapper {

    @Mapping(source = "usuario.id", target = "usuarioId")
    @Mapping(source = "usuario.nombreUsuario", target = "nombreUsuario")
    TurnoResponse toTurnoResponse(Turno turno);
}