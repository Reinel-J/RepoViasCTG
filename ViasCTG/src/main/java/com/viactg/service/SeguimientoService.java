package com.viactg.service;

import com.mongodb.client.result.UpdateResult;
import com.viactg.exception.BusinessRuleException;
import com.viactg.exception.ResourceNotFoundException;
import com.viactg.model.Confirmacion;
import com.viactg.model.EstadoReporte;
import com.viactg.model.Reporte;
import com.viactg.model.Seguimiento;
import com.viactg.model.Usuario;
import com.viactg.repository.ConfirmacionRepository;
import com.viactg.repository.ReporteRepository;
import com.viactg.repository.SeguimientoRepository;
import com.viactg.repository.UsuarioRepository;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * "¿Ya lo arreglaron?": votos de la comunidad sobre si un daño sigue y recordatorios periódicos para reportes
 * que llevan tiempo sin cambios. Los votos no cambian el estado; son una señal para los moderadores.
 */
@Service
public class SeguimientoService {
    private static final Logger log = LoggerFactory.getLogger(SeguimientoService.class);

    private final SeguimientoRepository seguimientoRepository;
    private final ReporteRepository reporteRepository;
    private final ConfirmacionRepository confirmacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionService notificacionService;
    private final CorreoService correoService;
    private final MongoTemplate mongoTemplate;
    private final Duration intervalo;
    private final String frontendUrl;

    public SeguimientoService(SeguimientoRepository seguimientoRepository, ReporteRepository reporteRepository,
                              ConfirmacionRepository confirmacionRepository, UsuarioRepository usuarioRepository,
                              NotificacionService notificacionService, CorreoService correoService, MongoTemplate mongoTemplate,
                              @Value("${app.seguimiento.dias:30}") long dias,
                              @Value("${app.frontend.url:http://localhost:5173}") String frontendUrl) {
        this.seguimientoRepository = seguimientoRepository;
        this.reporteRepository = reporteRepository;
        this.confirmacionRepository = confirmacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.notificacionService = notificacionService;
        this.correoService = correoService;
        this.mongoTemplate = mongoTemplate;
        this.intervalo = Duration.ofDays(dias);
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    /** Registra o cambia el voto del usuario. Solo para reportes activos. */
    public Resumen responder(String reporteId, String usuarioId, boolean arreglado) {
        Reporte reporte = buscarReporte(reporteId);
        if (!reporte.getEstado().esActivo()) {
            throw new BusinessRuleException("Este reporte ya está cerrado; no admite más respuestas");
        }
        Query voto = Query.query(Criteria.where("reporteId").is(reporteId).and("usuarioId").is(usuarioId));
        Update cambio = new Update().set("arreglado", arreglado).set("fecha", Instant.now());
        try {
            mongoTemplate.upsert(voto, cambio, Seguimiento.class);
        } catch (DuplicateKeyException carrera) {
            // Dos peticiones simultáneas del mismo usuario: la otra ya insertó, así que ahora es una actualización.
            mongoTemplate.upsert(voto, cambio, Seguimiento.class);
        }
        return resumen(reporteId, usuarioId);
    }

    /** Conteo de votos de un reporte; {@code usuarioId} puede ser null (visitante sin sesión). */
    public Resumen resumen(String reporteId, String usuarioId) {
        buscarReporte(reporteId);
        Instant ultima = seguimientoRepository.findFirstByReporteIdOrderByFechaDesc(reporteId).map(Seguimiento::getFecha).orElse(null);
        Boolean miRespuesta = usuarioId == null ? null
                : seguimientoRepository.findByReporteIdAndUsuarioId(reporteId, usuarioId).map(Seguimiento::isArreglado).orElse(null);
        return new Resumen(reporteId, seguimientoRepository.countByReporteIdAndArreglado(reporteId, true),
                seguimientoRepository.countByReporteIdAndArreglado(reporteId, false), ultima, miRespuesta);
    }

    /** Conteos de todos los reportes con votos, en una sola agregación ($group por reporte). */
    public List<Resumen> resumenes() {
        Aggregation agregacion = Aggregation.newAggregation(Aggregation.group("reporteId")
                .sum(ConditionalOperators.when(Criteria.where("arreglado").is(true)).then(1).otherwise(0)).as("arreglado")
                .sum(ConditionalOperators.when(Criteria.where("arreglado").is(false)).then(1).otherwise(0)).as("sigue")
                .max("fecha").as("ultimaRespuesta"));
        return mongoTemplate.aggregate(agregacion, Seguimiento.class, Document.class).getMappedResults().stream()
                .map(grupo -> new Resumen(grupo.getString("_id"), ((Number) grupo.get("arreglado")).longValue(),
                        ((Number) grupo.get("sigue")).longValue(), grupo.getDate("ultimaRespuesta").toInstant(), null))
                .toList();
    }

    @Scheduled(cron = "${app.seguimiento.cron:0 0 9 * * *}", zone = "America/Bogota")
    public void tareaDiaria() {
        int recordados = enviarRecordatorios(Instant.now());
        if (recordados > 0) log.info("Recordatorios de seguimiento enviados para {} reporte(s)", recordados);
    }

    /**
     * Pregunta al autor y a quienes confirmaron si el daño sigue, para cada reporte activo sin cambios ni
     * recordatorios en el intervalo configurado. Devuelve cuántos reportes se recordaron.
     */
    public int enviarRecordatorios(Instant ahora) {
        Instant limite = ahora.minus(intervalo);
        int recordados = 0;
        for (Reporte reporte : mongoTemplate.find(Query.query(pendienteDeRecordar(limite)), Reporte.class)) {
            // Se marca antes de avisar y con el mismo filtro: si otra ejecución ya lo tomó, no se avisa dos veces.
            UpdateResult marcado = mongoTemplate.updateFirst(
                    Query.query(new Criteria().andOperator(Criteria.where("_id").is(reporte.getId()), pendienteDeRecordar(limite))),
                    new Update().set("fechaUltimoSeguimiento", ahora), Reporte.class);
            if (marcado.getModifiedCount() == 0) continue;
            try {
                avisar(reporte);
                recordados++;
            } catch (RuntimeException exception) {
                log.warn("No fue posible enviar el recordatorio del reporte {}", reporte.getId(), exception);
            }
        }
        return recordados;
    }

    private Criteria pendienteDeRecordar(Instant limite) {
        return new Criteria().andOperator(
                Criteria.where("estado").in(EstadoReporte.ACTIVOS),
                Criteria.where("fechaActualizacion").lt(limite),
                new Criteria().orOperator(Criteria.where("fechaUltimoSeguimiento").is(null),
                        Criteria.where("fechaUltimoSeguimiento").lt(limite)));
    }

    private void avisar(Reporte reporte) {
        Set<String> destinatarios = new LinkedHashSet<>();
        destinatarios.add(reporte.getUsuarioId());
        confirmacionRepository.findByReporteId(reporte.getId()).stream().map(Confirmacion::getUsuarioId).forEach(destinatarios::add);
        String descripcion = resumir(reporte.getDescripcion());
        String enlace = frontendUrl + "/reportes/" + reporte.getId();
        for (Usuario usuario : usuarioRepository.findAllById(destinatarios)) {
            if (!usuario.isActivo()) continue;
            boolean esAutor = usuario.getId().equals(reporte.getUsuarioId());
            String pregunta = esAutor
                    ? "¿El daño de tu reporte \"" + descripcion + "\" sigue ahí? Cuéntanos si ya lo arreglaron."
                    : "¿El daño que confirmaste (\"" + descripcion + "\") sigue ahí? Cuéntanos si ya lo arreglaron.";
            notificacionService.crear(usuario.getId(), reporte.getId(), pregunta);
            correoService.enviar(usuario.getEmail(), "¿Ya arreglaron este daño? · ViaCTG",
                    "Hola " + usuario.getNombre() + ",\n\n" + pregunta + "\n\nResponde en un clic aquí:\n\n" + enlace + "\n\n"
                            + "Tu respuesta ayuda a los moderadores a saber qué calles ya se arreglaron.");
        }
    }

    private Reporte buscarReporte(String reporteId) {
        return reporteRepository.findById(reporteId).orElseThrow(() -> new ResourceNotFoundException("Reporte no encontrado: " + reporteId));
    }

    private static String resumir(String descripcion) {
        if (descripcion == null) return "";
        return descripcion.length() <= 60 ? descripcion : descripcion.substring(0, 57) + "...";
    }

    public record Resumen(String reporteId, long arreglado, long sigue, Instant ultimaRespuesta, Boolean miRespuesta) {
    }
}
