package com.viactg.mapper;

import com.viactg.dto.NotificacionResponse;
import com.viactg.model.Notificacion;
import org.springframework.stereotype.Component;

@Component
public class NotificacionMapper {
    public NotificacionResponse toResponse(Notificacion notificacion) {
        return new NotificacionResponse(notificacion.getId(), notificacion.getUsuarioId(), notificacion.getReporteId(),
                notificacion.getMensaje(), notificacion.isLeida(), notificacion.getFecha());
    }
}
