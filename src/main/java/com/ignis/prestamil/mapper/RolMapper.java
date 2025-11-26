package com.ignis.prestamil.mapper;

import com.ignis.prestamil.model.Rol;
import com.ignis.prestamil.response.RolResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface RolMapper {

    RolMapper INSTANCE = Mappers.getMapper(RolMapper.class);

    @Mapping(source = "rol", target = "nombre")
    RolResponse toRolResponse(Rol rol);
}
