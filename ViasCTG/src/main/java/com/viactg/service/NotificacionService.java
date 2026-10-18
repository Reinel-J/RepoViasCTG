package com.viactg.service;

import com.viactg.exception.ForbiddenOperationException;
import com.viactg.exception.ResourceNotFoundException;
import com.viactg.model.Notificacion;
import com.viactg.repository.NotificacionRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class NotificacionService {
    private final NotificacionRepository notificacionRepository;
    private final MongoTemplate mongoTemplate;

    public NotificacionService(NotificacionRepository notificacionRepository, MongoTemplate mongoTemplate) {
        this.notificacionRepository = notificacionRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public Notificacion crear(String usuarioId, String reporteId, String mensaje) {
        return notificacionRepository.save(Notificacion.builder().usuarioId(usuarioId).reporteId(reporteId)
                .mensaje(mensaje).leida(false).fecha(Instant.now()).build());
    }

    public List<Notificacion> listarParaUsuario(String usuarioId) { return notificacionRepository.findByUsuarioIdOrderByFechaDesc(usuarioId); }

    /** Cantidad de notificaciones sin leer del usuario, contada en la base de datos (la consulta la campana). */
    public long contarSinLeer(String usuarioId) {
        return mongoTemplate.count(Query.query(Criteria.where("usuarioId").is(usuarioId).and("leida").is(false)), Notificacion.class);
    }

    public Notificacion marcarLeida(String notificacionId, String usuarioId) {
        Notificacion notificacion = notificacionRepository.findById(notificacionId)
                .orElseThrow(() -> new ResourceNotFoundException("Notificación no encontrada: " + notificacionId));
        if (!notificacion.getUsuarioId().equals(usuarioId)) throw new ForbiddenOperationException("No puedes modificar esta notificación");
        notificacion.setLeida(true);
        return notificacionRepository.save(notificacion);
    }

    /** Marca como leídas todas las notificaciones pendientes del usuario; devuelve cuántas cambiaron. */
    public long marcarTodasLeidas(String usuarioId) {
        return mongoTemplate.updateMulti(Query.query(Criteria.where("usuarioId").is(usuarioId).and("leida").is(false)),
                new Update().set("leida", true), Notificacion.class).getModifiedCount();
    }
}
