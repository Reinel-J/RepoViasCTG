package com.viactg.mapper;

import com.viactg.dto.HistorialEstadoResponse;
import com.viactg.dto.ReporteResponse;
import com.viactg.model.HistorialEstado;
import com.viactg.model.Reporte;
import org.springframework.stereotype.Component;

@Component
public class ReporteMapper {
    public ReporteResponse toResponse(Reporte reporte) {
        return new ReporteResponse(reporte.getId(), reporte.getUsuarioId(), reporte.getCalleId(),
                reporte.getCategoriaId(), reporte.getDireccionOsm(), reporte.getDescripcion(), reporte.getFotoUrl(), reporte.getLatitud(),
                reporte.getLongitud(), reporte.getEstado(), reporte.getPrioridad(), reporte.getFechaCreacion(),
                reporte.getFechaActualizacion(), reporte.getHistorialEstados().stream().map(this::toHistorialResponse).toList());
    }

    public HistorialEstadoResponse toHistorialResponse(HistorialEstado historial) {
        return new HistorialEstadoResponse(historial.getId(), historial.getUsuarioAdminId(),
                historial.getEstadoAnterior(), historial.getEstadoNuevo(), historial.getComentarioAdmin(),
                historial.getFecha());
    }
}
