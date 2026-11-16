package com.viactg.mapper;

import com.viactg.dto.CategoriaReporteResponse;
import com.viactg.dto.HistorialEstadoResponse;
import com.viactg.dto.ReporteMapaResponse;
import com.viactg.dto.ReporteResponse;
import com.viactg.model.HistorialEstado;
import com.viactg.model.Reporte;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReporteMapper {
    public ReporteResponse toResponse(Reporte reporte) {
        return new ReporteResponse(reporte.getId(), reporte.getUsuarioId(), reporte.getCalleId(),
                categoria(reporte), reporte.getDireccionOsm(), reporte.getBarrioOsm(), reporte.getDescripcion(),
                reporte.getFotos() == null ? List.of() : List.copyOf(reporte.getFotos()), reporte.getLatitud(),
                reporte.getLongitud(), reporte.getEstado(), reporte.getPrioridad(), reporte.getFechaCreacion(),
                reporte.getFechaActualizacion(), reporte.getHistorialEstados() == null ? List.of()
                        : reporte.getHistorialEstados().stream().map(this::toHistorialResponse).toList());
    }

    public ReporteMapaResponse toMapaResponse(Reporte reporte) {
        String foto = reporte.getFotos() == null || reporte.getFotos().isEmpty() ? null : reporte.getFotos().get(0);
        return new ReporteMapaResponse(reporte.getId(), reporte.getLatitud(), reporte.getLongitud(), reporte.getEstado(),
                categoria(reporte), reporte.getDescripcion(), reporte.getDireccionOsm(), reporte.getBarrioOsm(), foto);
    }

    private static CategoriaReporteResponse categoria(Reporte reporte) {
        return reporte.getCategoria() == null ? null
                : new CategoriaReporteResponse(reporte.getCategoria().getId(), reporte.getCategoria().getNombre());
    }

    public HistorialEstadoResponse toHistorialResponse(HistorialEstado historial) {
        return new HistorialEstadoResponse(historial.getId(), historial.getUsuarioAdminId(),
                historial.getEstadoAnterior(), historial.getEstadoNuevo(), historial.getComentarioAdmin(),
                historial.getFecha());
    }
}
