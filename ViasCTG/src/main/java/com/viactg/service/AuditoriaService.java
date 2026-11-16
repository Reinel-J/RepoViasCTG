package com.viactg.service;

import com.viactg.dto.PaginaResponse;
import com.viactg.dto.RegistroAuditoriaResponse;
import com.viactg.model.RegistroAuditoria;
import com.viactg.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AuditoriaService {
    public static final String ACTOR_SISTEMA = "sistema";

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    private final MongoTemplate mongoTemplate;
    private final UsuarioRepository usuarioRepository;
    private final ApplicationEventPublisher eventos;

    public AuditoriaService(MongoTemplate mongoTemplate, UsuarioRepository usuarioRepository, ApplicationEventPublisher eventos) {
        this.mongoTemplate = mongoTemplate;
        this.usuarioRepository = usuarioRepository;
        this.eventos = eventos;
    }

    public void registrar(String actorId, String accion, String entidad, String entidadId, String detalle) {
        try {
            String nombre = ACTOR_SISTEMA.equals(actorId)
                    ? "Sistema"
                    : usuarioRepository.findById(actorId)
                            .map(usuario -> usuario.getNombre())
                            .orElse("Usuario eliminado");

            RegistroAuditoria registro = RegistroAuditoria.builder()
                    .fecha(Instant.now())
                    .actorId(actorId)
                    .actorNombre(nombre)
                    .accion(accion)
                    .entidad(entidad)
                    .entidadId(entidadId)
                    .detalle(detalle)
                    .build();
            RegistroAuditoria guardado = mongoTemplate.insert(registro);
            if (guardado != null) eventos.publishEvent(CambioReplicable.auditoria(guardado.getId()));
        } catch (RuntimeException exception) {
            log.warn(
                    "No fue posible guardar la auditoría de {} sobre {} {}",
                    accion,
                    entidad,
                    entidadId,
                    exception
            );
        }
    }

    public PaginaResponse<RegistroAuditoriaResponse> listar(
            String entidad,
            String accion,
            String actorId,
            Instant desde,
            Instant hasta,
            int pagina,
            int tamano
    ) {
        Query query = new Query();

        if (entidad != null && !entidad.isBlank()) {
            query.addCriteria(Criteria.where("entidad").is(entidad));
        }
        if (accion != null && !accion.isBlank()) {
            query.addCriteria(Criteria.where("accion").is(accion));
        }
        if (actorId != null && !actorId.isBlank()) {
            query.addCriteria(Criteria.where("actorId").is(actorId));
        }
        if (desde != null || hasta != null) {
            Criteria fecha = Criteria.where("fecha");
            if (desde != null) {
                fecha.gte(desde);
            }
            if (hasta != null) {
                fecha.lte(hasta);
            }
            query.addCriteria(fecha);
        }

        long total = mongoTemplate.count(query, RegistroAuditoria.class);
        List<RegistroAuditoriaResponse> contenido = mongoTemplate.find(
                        query.with(Sort.by(Sort.Direction.DESC, "fecha"))
                                .skip((long) pagina * tamano)
                                .limit(tamano),
                        RegistroAuditoria.class
                )
                .stream()
                .map(registro -> new RegistroAuditoriaResponse(
                        registro.getId(),
                        registro.getFecha(),
                        registro.getActorId(),
                        registro.getActorNombre(),
                        registro.getAccion(),
                        registro.getEntidad(),
                        registro.getEntidadId(),
                        registro.getDetalle()
                ))
                .toList();

        return new PaginaResponse<>(
                contenido,
                pagina,
                tamano,
                total,
                (int) Math.ceil((double) total / tamano)
        );
    }
}
