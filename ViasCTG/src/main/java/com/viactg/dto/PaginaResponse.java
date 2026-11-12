package com.viactg.dto;

import java.util.List;

/** Una página de resultados. {@code pagina} empieza en 0; {@code totalElementos} cuenta todos los que cumplen el filtro. */
public record PaginaResponse<T>(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {
}
