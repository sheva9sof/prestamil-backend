package com.ignis.prestamil.mapper;

import com.ignis.prestamil.model.Opcion;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.response.LoginResponse;
import com.ignis.prestamil.response.MenuResponse;
import com.ignis.prestamil.response.UsuarioResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UsuarioMapper {

    @Mapping(source = "usuario.id", target = "id")
    @Mapping(source = "usuario.nombreUsuario", target = "username")
    @Mapping(source = "usuario.rol.id", target = "idRol")
    @Mapping(source = "usuario.rol.rol", target = "rolNombre")
    @Mapping(source = "opciones", target = "opciones")
    LoginResponse toLoginResponse(Usuario usuario, List<Opcion> opciones);

    @Mapping(source = "rol.rol", target = "rolNombre")
    @Mapping(source = "rol.id", target = "idRol")
    UsuarioResponse toUsuarioResponse(Usuario usuario);

    @Mapping(source = "nombreIcono", target = "nombreIcono")
    @Mapping(source = "nombreIcono", target = "icono")
    @Mapping(target = "url", ignore = true)
    @Mapping(target = "descripcion", ignore = true)
    @Mapping(target = "submenus", ignore = true)
    MenuResponse toMenuResponse(Opcion opcion);
}
