package com.viactg.service;

import com.viactg.exception.ForbiddenOperationException;
import com.viactg.exception.ResourceNotFoundException;
import com.viactg.model.Notificacion;
import com.viactg.repository.NotificacionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class NotificacionService {
    private final NotificacionRepository notificacionRepository;

    public NotificacionService(NotificacionRepository notificacionRepository) { this.notificacionRepository = notificacionRepository; }

    public Notificacion crear(String usuarioId, String reporteId, String mensaje) {
        return notificacionRepository.save(Notificacion.builder().usuarioId(usuarioId).reporteId(reporteId)
                .mensaje(mensaje).leida(false).fecha(Instant.now()).build());
    }

    public List<Notificacion> listarParaUsuario(String usuarioId) { return notificacionRepository.findByUsuarioIdOrderByFechaDesc(usuarioId); }

    public Notificacion marcarLeida(String notificacionId, String usuarioId) {
        Notificacion notificacion = notificacionRepository.findById(notificacionId)
                .orElseThrow(() -> new ResourceNotFoundException("Notificación no encontrada: " + notificacionId));
        if (!notificacion.getUsuarioId().equals(usuarioId)) throw new ForbiddenOperationException("No puedes modificar esta notificación");
        notificacion.setLeida(true);
        return notificacionRepository.save(notificacion);
    }
}
