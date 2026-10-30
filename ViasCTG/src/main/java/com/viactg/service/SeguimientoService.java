package com.viactg.service;

import com.mongodb.client.result.UpdateResult;
import com.viactg.exception.BusinessRuleException;
import com.viactg.exception.ResourceNotFoundException;
import com.viactg.model.Confirmacion;
import com.viactg.model.EstadoReporte;
import com.viactg.model.HistorialEstado;
import com.viactg.model.Reporte;
import com.viactg.model.Rol;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * "¿Ya lo arreglaron?": votos de la comunidad sobre si un daño sigue y recordatorios periódicos para reportes
 * que llevan tiempo sin cambios. Los votos no cambian el estado; son una señal para los moderadores.
 */
@Service
public class SeguimientoService {
    private static final Logger log = LoggerFactory.getLogger(SeguimientoService.class);
    private static final DateTimeFormatter FECHA_AVISO = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' h:mm a")
            .withZone(ZoneId.of("America/Bogota"));

    private final SeguimientoRepository seguimientoRepository;
    private final ReporteRepository reporteRepository;
    private final ConfirmacionRepository confirmacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionService notificacionService;
    private final CorreoService correoService;
    private final MongoTemplate mongoTemplate;
    private final ReporteService reporteService;
    private final Duration intervalo;
    private final String frontendUrl;
    // Cierre por la comunidad: apagado por defecto, porque cambia quién decide que un daño está resuelto.
    private final boolean cierreHabilitado;
    private final CierreComunitario reglaCierre;
    private final Duration graciaCierre;

    public SeguimientoService(SeguimientoRepository seguimientoRepository, ReporteRepository reporteRepository,
                              ConfirmacionRepository confirmacionRepository, UsuarioRepository usuarioRepository,
                              NotificacionService notificacionService, CorreoService correoService, MongoTemplate mongoTemplate,
                              ReporteService reporteService,
                              @Value("${app.seguimiento.dias:30}") long dias,
                              @Value("${app.frontend.url:http://localhost:5173}") String frontendUrl,
                              @Value("${app.seguimiento.cierre.habilitado:false}") boolean cierreHabilitado,
                              @Value("${app.seguimiento.cierre.minimo-votos:3}") int minimoVotos,
                              @Value("${app.seguimiento.cierre.proporcion:0.75}") double proporcion,
                              @Value("${app.seguimiento.cierre.gracia-horas:72}") long graciaHoras) {
        this.seguimientoRepository = seguimientoRepository;
        this.reporteRepository = reporteRepository;
        this.confirmacionRepository = confirmacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.notificacionService = notificacionService;
        this.correoService = correoService;
        this.mongoTemplate = mongoTemplate;
        this.reporteService = reporteService;
        this.intervalo = Duration.ofDays(dias);
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.cierreHabilitado = cierreHabilitado;
        this.reglaCierre = new CierreComunitario(minimoVotos, proporcion);
        this.graciaCierre = Duration.ofHours(graciaHoras);
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
        if (cierreHabilitado && reporte.getEstado() == EstadoReporte.EN_PROCESO) {
            reevaluarCierre(reporteId, Instant.now());
        }
        return resumen(reporteId, usuarioId);
    }

    /** Conteo de votos de un reporte; {@code usuarioId} puede ser null (visitante sin sesión). */
    public Resumen resumen(String reporteId, String usuarioId) {
        Reporte reporte = buscarReporte(reporteId);
        Instant ultima = seguimientoRepository.findFirstByReporteIdOrderByFechaDesc(reporteId).map(Seguimiento::getFecha).orElse(null);
        Boolean miRespuesta = usuarioId == null ? null
                : seguimientoRepository.findByReporteIdAndUsuarioId(reporteId, usuarioId).map(Seguimiento::isArreglado).orElse(null);
        return new Resumen(reporteId, seguimientoRepository.countByReporteIdAndArreglado(reporteId, true),
                seguimientoRepository.countByReporteIdAndArreglado(reporteId, false), ultima, miRespuesta, cierrePrevisto(reporte),
                progresoCierre(reporte));
    }

    /**
     * Los votos tal como los cuenta la regla de cierre (solo los recientes, el del autor doble) junto a lo que exige.
     * Sin esto, la página mostraba solo personas y no cuadraba con la regla. Null si el cierre está apagado o el
     * reporte no está en proceso.
     */
    private ProgresoCierre progresoCierre(Reporte reporte) {
        if (!cierreHabilitado || reporte.getEstado() != EstadoReporte.EN_PROCESO) return null;
        CierreComunitario.Evaluacion evaluacion = evaluarCierre(reporte);
        return new ProgresoCierre(evaluacion.arreglado(), evaluacion.sigue(), reglaCierre.minimoVotos(), reglaCierre.proporcionMinima());
    }

    /** Conteos de todos los reportes con votos, en una sola agregación ($group por reporte). */
    public List<Resumen> resumenes() {
        Map<String, Instant> cierres = cierresPrevistos();
        Aggregation agregacion = Aggregation.newAggregation(Aggregation.group("reporteId")
                .sum(ConditionalOperators.when(Criteria.where("arreglado").is(true)).then(1).otherwise(0)).as("arreglado")
                .sum(ConditionalOperators.when(Criteria.where("arreglado").is(false)).then(1).otherwise(0)).as("sigue")
                .max("fecha").as("ultimaRespuesta"));
        return mongoTemplate.aggregate(agregacion, Seguimiento.class, Document.class).getMappedResults().stream()
                .map(grupo -> new Resumen(grupo.getString("_id"), ((Number) grupo.get("arreglado")).longValue(),
                        ((Number) grupo.get("sigue")).longValue(), grupo.getDate("ultimaRespuesta").toInstant(), null,
                        cierres.get(grupo.getString("_id"))))
                .toList();
    }

    @Scheduled(cron = "${app.seguimiento.cron:0 0 9 * * *}", zone = "America/Bogota")
    public void tareaDiaria() {
        Ejecucion ejecucion = ejecutarTareaDiaria(Instant.now());
        if (ejecucion.recordados() > 0) log.info("Recordatorios de seguimiento enviados para {} reporte(s)", ejecucion.recordados());
        if (ejecucion.cerrados() > 0) log.info("{} reporte(s) cerrados por los votos de la comunidad", ejecucion.cerrados());
    }

    /** Lo que hace la tarea diaria: recordatorios y cierres por la comunidad cuya gracia venció. */
    public Ejecucion ejecutarTareaDiaria(Instant ahora) {
        return new Ejecucion(enviarRecordatorios(ahora), aplicarCierresComunitarios(ahora));
    }

    /**
     * Cierra los reportes en proceso que siguen cumpliendo la regla al vencer la gracia. Se vuelve a evaluar: entre
     * tanto pudieron llegar votos «sigue igual» o un «mantener abierto». Devuelve cuántos cerró.
     */
    public int aplicarCierresComunitarios(Instant ahora) {
        if (!cierreHabilitado) return 0;
        Query vencidos = Query.query(Criteria.where("estado").is(EstadoReporte.EN_PROCESO)
                .and("cierreComunitarioDesde").lte(ahora.minus(graciaCierre)));
        int cerrados = 0;
        for (Reporte reporte : mongoTemplate.find(vencidos, Reporte.class)) {
            CierreComunitario.Evaluacion evaluacion = evaluarCierre(reporte);
            if (!evaluacion.candidato()) {
                quitarCandidatura(reporte.getId());
                continue;
            }
            try {
                reporteService.cerrarPorLaComunidad(reporte.getId(), "Resuelto por la comunidad: "
                        + (evaluacion.arreglado() == 1 ? "1 voto dice" : evaluacion.arreglado() + " votos dicen") + " que ya está arreglado y "
                        + evaluacion.sigue() + " que sigue igual (el voto del autor cuenta doble).");
                cerrados++;
            } catch (BusinessRuleException cambioSimultaneo) {
                // Un moderador lo cambió de estado mientras tanto: su decisión prevalece.
                log.info("El reporte {} cambió de estado antes del cierre por la comunidad", reporte.getId());
            }
        }
        return cerrados;
    }

    /** Un moderador descarta la propuesta de cierre: solo contarán los votos que lleguen desde ahora. */
    public Resumen mantenerAbierto(String reporteId) {
        Reporte reporte = buscarReporte(reporteId);
        if (reporte.getEstado() != EstadoReporte.EN_PROCESO) {
            throw new BusinessRuleException("Solo un reporte en proceso puede tener una propuesta de cierre por la comunidad");
        }
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(reporteId).and("estado").is(EstadoReporte.EN_PROCESO)),
                new Update().unset("cierreComunitarioDesde").set("cierreComunitarioVetadoEn", Instant.now()), Reporte.class);
        return resumen(reporteId, null);
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

    // Marca o desmarca la candidatura según los votos actuales. Los filtros por estado y por el valor anterior evitan
    // pisar un cambio simultáneo; solo quien logra marcarla avisa a los moderadores.
    private void reevaluarCierre(String reporteId, Instant ahora) {
        Reporte reporte = buscarReporte(reporteId);
        if (reporte.getEstado() != EstadoReporte.EN_PROCESO) return;
        boolean candidato = evaluarCierre(reporte).candidato();
        if (candidato && reporte.getCierreComunitarioDesde() == null) {
            UpdateResult marcado = mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(reporteId)
                            .and("estado").is(EstadoReporte.EN_PROCESO).and("cierreComunitarioDesde").is(null)),
                    new Update().set("cierreComunitarioDesde", ahora), Reporte.class);
            if (marcado.getModifiedCount() > 0) avisarModeradores(reporte, ahora.plus(graciaCierre));
        } else if (!candidato && reporte.getCierreComunitarioDesde() != null) {
            quitarCandidatura(reporteId);
        }
    }

    private void quitarCandidatura(String reporteId) {
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(reporteId)), new Update().unset("cierreComunitarioDesde"), Reporte.class);
    }

    /**
     * Solo cuentan los votos desde que el reporte pasó a EN_PROCESO (un «sigue igual» de antes de que llegara la
     * cuadrilla no dice nada) y, si un moderador lo mantuvo abierto, desde ese momento.
     */
    private CierreComunitario.Evaluacion evaluarCierre(Reporte reporte) {
        Instant desde = enProcesoDesde(reporte);
        Instant vetado = reporte.getCierreComunitarioVetadoEn();
        if (vetado != null && vetado.isAfter(desde)) desde = vetado;
        Instant inicio = desde;
        List<CierreComunitario.Voto> votos = seguimientoRepository.findByReporteId(reporte.getId()).stream()
                .filter(voto -> voto.getFecha() != null && !voto.getFecha().isBefore(inicio))
                .map(voto -> new CierreComunitario.Voto(voto.getUsuarioId(), voto.isArreglado()))
                .toList();
        return reglaCierre.evaluar(votos, reporte.getUsuarioId());
    }

    private static Instant enProcesoDesde(Reporte reporte) {
        List<HistorialEstado> historial = reporte.getHistorialEstados() == null ? List.of() : reporte.getHistorialEstados();
        for (int i = historial.size() - 1; i >= 0; i--) {
            if (historial.get(i).getEstadoNuevo() == EstadoReporte.EN_PROCESO) return historial.get(i).getFecha();
        }
        return reporte.getFechaActualizacion() == null ? Instant.EPOCH : reporte.getFechaActualizacion();
    }

    private Instant cierrePrevisto(Reporte reporte) {
        if (!cierreHabilitado || reporte.getEstado() != EstadoReporte.EN_PROCESO || reporte.getCierreComunitarioDesde() == null) return null;
        return reporte.getCierreComunitarioDesde().plus(graciaCierre);
    }

    private Map<String, Instant> cierresPrevistos() {
        if (!cierreHabilitado) return Map.of();
        Query candidatos = Query.query(Criteria.where("estado").is(EstadoReporte.EN_PROCESO).and("cierreComunitarioDesde").ne(null));
        return mongoTemplate.find(candidatos, Reporte.class).stream()
                .collect(Collectors.toMap(Reporte::getId, reporte -> reporte.getCierreComunitarioDesde().plus(graciaCierre)));
    }

    // Los fallos al notificar solo se registran: no deben deshacer la propuesta de cierre.
    private void avisarModeradores(Reporte reporte, Instant cierre) {
        try {
            String mensaje = "La comunidad dice que \"" + resumir(reporte.getDescripcion()) + "\" ya está arreglado. Se cerrará solo el "
                    + FECHA_AVISO.format(cierre) + " si nadie lo mantiene abierto desde el panel.";
            for (Usuario moderador : usuarioRepository.findByRolInAndActivoTrue(List.of(Rol.ADMIN, Rol.MODERADOR))) {
                notificacionService.crear(moderador.getId(), reporte.getId(), mensaje);
            }
        } catch (RuntimeException exception) {
            log.warn("No fue posible avisar a los moderadores del cierre por la comunidad del reporte {}", reporte.getId(), exception);
        }
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

    /**
     * {@code arreglado} y {@code sigue} cuentan personas; {@code cierrePrevisto} es cuándo se cerrará solo por los votos
     * (null si no hay propuesta) y {@code progresoCierre}, los votos según la regla (null si no aplica).
     */
    public record Resumen(String reporteId, long arreglado, long sigue, Instant ultimaRespuesta, Boolean miRespuesta,
                          Instant cierrePrevisto, ProgresoCierre progresoCierre) {
        public Resumen(String reporteId, long arreglado, long sigue, Instant ultimaRespuesta, Boolean miRespuesta, Instant cierrePrevisto) {
            this(reporteId, arreglado, sigue, ultimaRespuesta, miRespuesta, cierrePrevisto, null);
        }
    }

    /** Votos ponderados que cuentan para el cierre por la comunidad y lo que exige la regla. */
    public record ProgresoCierre(int arreglado, int sigue, int minimoVotos, double proporcionMinima) {
    }

    public record Ejecucion(int recordados, int cerrados) {
    }
}
