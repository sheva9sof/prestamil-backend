package com.ignis.prestamil.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Página de resultados con forma estable para el frontend (no se serializa {@link Page} directo).
 * {@code page} empieza en 0.
 */
@Data
@AllArgsConstructor
public class PageResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static <T> PageResponse<T> desdePagina(Page<T> pagina) {
        return new PageResponse<>(pagina.getContent(), pagina.getNumber(), pagina.getSize(),
                pagina.getTotalElements(), pagina.getTotalPages());
    }

    /** Pagina en memoria una lista ya filtrada y ordenada. */
    public static <T> PageResponse<T> paginar(List<T> todos, int page, int size) {
        int desde = (int) Math.min((long) page * size, todos.size());
        int hasta = Math.min(desde + size, todos.size());
        int totalPages = (todos.size() + size - 1) / size;
        return new PageResponse<>(todos.subList(desde, hasta), page, size, todos.size(), totalPages);
    }
}
