package com.ignis.prestamil.mapper;

import com.ignis.prestamil.model.Opcion;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.response.LoginResponse;
import com.ignis.prestamil.response.MenuResponse;
import com.ignis.prestamil.response.UsuarioResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class UsuarioMapper {

    public LoginResponse toLoginResponse(Usuario usuario, List<Opcion> opciones) {
        if (usuario == null) {
            return null;
        }

        LoginResponse response = new LoginResponse();
        response.setNombreUsuario(usuario.getNombreUsuario());
        response.setNombre(usuario.getNombre());
        response.setApellidos(usuario.getApellidos());
        response.setEstatus(usuario.getEstatus());
        response.setCambiarPassword(usuario.getCambiarPassword());
        response.setUltimoLogin(usuario.getUltimoLogin());
        response.setIdRol(usuario.getRol() != null ? usuario.getRol().getId() : null);
        response.setFechaIni(usuario.getFechaIni());
        response.setFechaFin(usuario.getFechaFin());
        response.setVigencia(usuario.getVigencia());
        response.setAplicaCambioPassword(usuario.getAplicaCambioPassword());
        response.setFechaCambioPass(usuario.getFechaCambioPass());
        response.setEditable(usuario.getEditable());
        response.setSessionToken(usuario.getSessionToken());
        response.setUltimaActividad(usuario.getUltimaActividad());
        response.setInicioSesion(usuario.getInicioSesion());
        
        // Mapear opciones a MenuResponse agrupadas por idPadre
        if (opciones != null) {
            List<MenuResponse> menuResponses = agruparOpcionesPorPadre(opciones);
            response.setOpciones(menuResponses);
        }
        
        return response;
    }

    public LoginResponse toLoginResponse(Usuario usuario) {
        return toLoginResponse(usuario, null);
    }

    /**
     * Agrupa las opciones por idPadre y estructura el menú jerárquico anidado
     * Los menús con principalMenu = true son padres
     * Los menús con principalMenu = false e idPadre indican a qué padre pertenecen
     */
    private List<MenuResponse> agruparOpcionesPorPadre(List<Opcion> opciones) {
        // Filtrar opciones padre (principalMenu = true)
        List<Opcion> opcionesPadre = opciones.stream()
                .filter(opcion -> opcion.getPrincipalMenu() != null && opcion.getPrincipalMenu())
                .sorted(Comparator.comparing(Opcion::getId))
                .collect(Collectors.toList());
        
        // Agrupar opciones hijas por idPadre (principalMenu = false e idPadre != null)
        Map<Integer, List<Opcion>> opcionesPorPadre = opciones.stream()
                .filter(opcion -> (opcion.getPrincipalMenu() == null || !opcion.getPrincipalMenu())
                        && opcion.getIdPadre() != null)
                .collect(Collectors.groupingBy(
                    Opcion::getIdPadre,
                    LinkedHashMap::new,
                    Collectors.toList()
                ));
        
        // Crear lista de MenuResponse jerárquica anidada
        List<MenuResponse> menuResponses = new ArrayList<>();
        
        // Crear menús padre con sus submenús
        for (Opcion opcionPadre : opcionesPadre) {
            MenuResponse menuPadre = toMenuResponse(opcionPadre);
            
            // Obtener y agregar submenús de este padre
            List<Opcion> opcionesHijas = opcionesPorPadre.get(opcionPadre.getId());
            if (opcionesHijas != null && !opcionesHijas.isEmpty()) {
                opcionesHijas.sort(Comparator.comparing(Opcion::getId));
                for (Opcion opcionHija : opcionesHijas) {
                    MenuResponse menuHijo = toMenuResponse(opcionHija);
                    menuPadre.getSubmenus().add(menuHijo);
                }
            }
            
            menuResponses.add(menuPadre);
        }
        
        return menuResponses;
    }

    /**
     * Convierte una Opcion a MenuResponse
     */
    private MenuResponse toMenuResponse(Opcion opcion) {
        if (opcion == null) {
            return null;
        }

        MenuResponse menuResponse = new MenuResponse();
        menuResponse.setId(opcion.getId());
        menuResponse.setOpcion(opcion.getOpcion());
        menuResponse.setIcono(opcion.getIcono() != null ? opcion.getIcono().toString() : null);
        menuResponse.setNombreIcono(opcion.getNombreIcono());
        
        return menuResponse;
    }

    /**
     * Convierte un Usuario a UsuarioResponse
     */
    public UsuarioResponse toUsuarioResponse(Usuario usuario) {
        if (usuario == null) {
            return null;
        }

        UsuarioResponse response = new UsuarioResponse();
        response.setId(usuario.getId());
        response.setNombreUsuario(usuario.getNombreUsuario());
        response.setNombre(usuario.getNombre());
        response.setApellidos(usuario.getApellidos());
        response.setEstatus(usuario.getEstatus());
        response.setCambiarPassword(usuario.getCambiarPassword());
        response.setUltimoLogin(usuario.getUltimoLogin());
        response.setIdRol(usuario.getRol() != null ? usuario.getRol().getId() : null);
        response.setFechaIni(usuario.getFechaIni());
        response.setFechaFin(usuario.getFechaFin());
        response.setVigencia(usuario.getVigencia());
        response.setAplicaCambioPassword(usuario.getAplicaCambioPassword());
        response.setFechaCambioPass(usuario.getFechaCambioPass());
        response.setEditable(usuario.getEditable());
        response.setSessionToken(usuario.getSessionToken());
        response.setUltimaActividad(usuario.getUltimaActividad());
        response.setInicioSesion(usuario.getInicioSesion());
        response.setCreado(usuario.getCreado());
        
        return response;
    }

}

