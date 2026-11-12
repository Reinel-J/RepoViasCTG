package com.viactg.dto;

import java.util.List;

public record CambioEstadoLoteResponse(List<String> actualizados, List<Fallo> fallidos) {
    public record Fallo(String id, String motivo) {
    }
}
