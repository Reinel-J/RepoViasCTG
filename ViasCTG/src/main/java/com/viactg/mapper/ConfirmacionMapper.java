package com.viactg.mapper;

import com.viactg.dto.ConfirmacionResponse;
import com.viactg.model.Confirmacion;
import org.springframework.stereotype.Component;

@Component
public class ConfirmacionMapper {
    public ConfirmacionResponse toResponse(Confirmacion confirmacion) {
        return new ConfirmacionResponse(confirmacion.getId(), confirmacion.getReporteId(),
                confirmacion.getUsuarioId(), confirmacion.getFecha());
    }
}
