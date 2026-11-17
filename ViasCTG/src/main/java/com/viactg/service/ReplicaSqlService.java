package com.viactg.service;

import com.viactg.dto.EstadoReplicaResponse;
import com.viactg.exception.ServicioNoDisponibleException;
import com.viactg.model.Barrio;
import com.viactg.model.Categoria;
import com.viactg.model.Comentario;
import com.viactg.model.Confirmacion;
import com.viactg.model.HistorialEstado;
import com.viactg.model.RegistroAuditoria;
import com.viactg.model.Reporte;
import com.viactg.model.Seguimiento;
import com.viactg.model.Usuario;
import com.viactg.repository.sql.ReplicaSqlRepository;
import org.bson.Document;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Réplica de MongoDB (base principal) en PostgreSQL (base secundaria). Funciona en dos modos:
 * <ul>
 *   <li><b>Reconstrucción completa</b>: al arrancar, por pedido de un administrador o tras un error, aplica las
 *   migraciones de Flyway, vacía las tablas y copia todas las colecciones por lotes, en una sola transacción (mientras
 *   tanto, quien consulte PostgreSQL sigue viendo la copia anterior).</li>
 *   <li><b>Cambio a cambio</b>: los servicios publican un {@link CambioReplicable} después de escribir en Mongo; aquí se
 *   vuelve a leer ese documento y se deja su fila igual.</li>
 * </ul>
 * Todo corre en un único hilo en segundo plano: las peticiones HTTP no esperan a PostgreSQL y los cambios se aplican
 * en orden. Si PostgreSQL falla, la API sigue funcionando; se registra el error y una tarea periódica reconstruye la
 * réplica cuando vuelve a responder. Con {@code app.sql.habilitada=false} no hace nada.
 */
@Service
public class ReplicaSqlService {
    private static final Logger log = LoggerFactory.getLogger(ReplicaSqlService.class);
    static final int TAMANO_LOTE = 500;

    private final MongoTemplate mongoTemplate;
    private final ReplicaSqlRepository repositorio;
    private final Flyway flyway;
    private final TransactionTemplate transacciones;
    private final ExecutorService hilo = Executors.newSingleThreadExecutor(tarea -> {
        Thread thread = new Thread(tarea, "replica-sql");
        thread.setDaemon(true);
        return thread;
    });

    // Hasta la primera reconstrucción no se aplican cambios sueltos: el esquema podría no existir todavía.
    private volatile boolean pendienteReconstruir = true;
    private volatile Instant ultimaSincronizacion;
    private volatile Instant ultimaReconstruccion;
    private volatile String ultimoError;
    private volatile Instant ultimoErrorEn;

    public ReplicaSqlService(MongoTemplate mongoTemplate, ObjectProvider<ReplicaSqlRepository> repositorio,
                             ObjectProvider<Flyway> flyway, ObjectProvider<TransactionTemplate> transacciones) {
        this.mongoTemplate = mongoTemplate;
        this.repositorio = repositorio.getIfAvailable();
        this.flyway = flyway.getIfAvailable();
        this.transacciones = transacciones.getIfAvailable();
    }

    public boolean habilitada() {
        return repositorio != null && flyway != null && transacciones != null;
    }

    /** Al terminar de arrancar (después de migraciones y datos iniciales), se reconstruye la réplica completa. */
    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        if (habilitada()) programarReconstruccion();
    }

    @EventListener
    public void alCambiar(CambioReplicable cambio) {
        if (!habilitada()) return;
        hilo.execute(() -> {
            if (cambio.entidad() == CambioReplicable.Entidad.TODO) reconstruir();
            else aplicar(cambio);
        });
    }

    /** Si la última copia falló, la reintenta completa cada {@code app.sql.reintento-ms}. */
    @Scheduled(fixedDelayString = "${app.sql.reintento-ms:300000}", initialDelayString = "${app.sql.reintento-ms:300000}")
    public void reintentar() {
        if (habilitada() && pendienteReconstruir && ultimoError != null) programarReconstruccion();
    }

    /** Pide una reconstrucción completa en segundo plano (botón del panel). */
    public void solicitarReconstruccion() {
        if (!habilitada()) throw new ServicioNoDisponibleException("La réplica SQL no está habilitada (SQL_REPLICA_HABILITADA=true)");
        programarReconstruccion();
    }

    private void programarReconstruccion() {
        pendienteReconstruir = true;
        hilo.execute(this::reconstruir);
    }

    // ------------------------------------------------------------------ reconstrucción completa

    void reconstruir() {
        Instant inicio = Instant.now();
        try {
            flyway.migrate();
            Map<String, Long> filas = transacciones.execute(estado -> {
                repositorio.vaciar();
                copiar(Usuario.class, repositorio::guardarUsuarios);
                copiar(Categoria.class, repositorio::guardarCategorias);
                copiar(Barrio.class, repositorio::guardarBarrios);
                copiar(Reporte.class, repositorio::guardarReportes);
                copiar(Comentario.class, repositorio::guardarComentarios);
                copiar(Confirmacion.class, repositorio::insertarConfirmaciones);
                copiar(Seguimiento.class, repositorio::insertarSeguimientos);
                copiar(RegistroAuditoria.class, repositorio::insertarAuditoria);
                return repositorio.contarFilas();
            });
            pendienteReconstruir = false;
            ultimoError = null;
            ultimaReconstruccion = Instant.now();
            ultimaSincronizacion = ultimaReconstruccion;
            log.info("Réplica SQL reconstruida en {} ms: {}", ultimaReconstruccion.toEpochMilli() - inicio.toEpochMilli(), filas);
        } catch (RuntimeException exception) {
            registrarError("No fue posible reconstruir la réplica SQL", exception);
        }
    }

    // Lee la colección en flujo (sin cargarla entera en memoria) y la escribe por lotes.
    private <T> void copiar(Class<T> tipo, Consumer<List<T>> guardar) {
        List<T> lote = new ArrayList<>(TAMANO_LOTE);
        try (var documentos = mongoTemplate.stream(new Query(), tipo)) {
            documentos.forEach(documento -> {
                lote.add(documento);
                if (lote.size() == TAMANO_LOTE) {
                    guardar.accept(List.copyOf(lote));
                    lote.clear();
                }
            });
        }
        if (!lote.isEmpty()) guardar.accept(lote);
    }

    // ------------------------------------------------------------------ cambio a cambio

    void aplicar(CambioReplicable cambio) {
        // Una reconstrucción completa ya está en camino y copiará también este cambio.
        if (pendienteReconstruir || cambio.id() == null) return;
        try {
            transacciones.executeWithoutResult(estado -> aplicarEnTransaccion(cambio));
            ultimaSincronizacion = Instant.now();
        } catch (RuntimeException exception) {
            registrarError("No fue posible copiar a la réplica SQL " + cambio.entidad() + " " + cambio.id(), exception);
            pendienteReconstruir = true;
        }
    }

    private void aplicarEnTransaccion(CambioReplicable cambio) {
        String id = cambio.id();
        switch (cambio.entidad()) {
            case USUARIO -> buscar(id, Usuario.class).ifPresent(usuario -> repositorio.guardarUsuarios(List.of(usuario)));
            case CATEGORIA -> buscar(id, Categoria.class).ifPresent(categoria -> repositorio.guardarCategorias(List.of(categoria)));
            case BARRIO -> buscar(id, Barrio.class).ifPresentOrElse(barrio -> repositorio.guardarBarrios(List.of(barrio)),
                    () -> repositorio.eliminarBarrio(id));
            case REPORTE -> buscar(id, Reporte.class).ifPresentOrElse(this::guardarReporte, () -> repositorio.eliminarReporte(id));
            case COMENTARIO -> buscar(id, Comentario.class).ifPresentOrElse(comentario -> {
                asegurarReporte(comentario.getReporteId());
                asegurarUsuario(comentario.getUsuarioId());
                repositorio.guardarComentarios(List.of(comentario));
            }, () -> repositorio.eliminarComentario(id));
            case CONFIRMACIONES_DE_REPORTE -> {
                List<Confirmacion> confirmaciones = delReporte(id, Confirmacion.class);
                asegurarReporte(id);
                confirmaciones.forEach(confirmacion -> asegurarUsuario(confirmacion.getUsuarioId()));
                repositorio.reemplazarConfirmaciones(id, confirmaciones);
            }
            case SEGUIMIENTOS_DE_REPORTE -> {
                List<Seguimiento> votos = delReporte(id, Seguimiento.class);
                asegurarReporte(id);
                votos.forEach(voto -> asegurarUsuario(voto.getUsuarioId()));
                repositorio.reemplazarSeguimientos(id, votos);
            }
            case AUDITORIA -> buscar(id, RegistroAuditoria.class).ifPresent(registro -> repositorio.insertarAuditoria(List.of(registro)));
            case TODO -> throw new IllegalStateException("TODO se atiende con una reconstrucción completa");
        }
    }

    // Las filas que el reporte referencia (autor, categoría, moderadores del historial) deben existir antes.
    private void guardarReporte(Reporte reporte) {
        asegurarUsuario(reporte.getUsuarioId());
        if (reporte.getCategoria() != null) asegurarCategoria(reporte.getCategoria().getId());
        Set<String> moderadores = new LinkedHashSet<>();
        if (reporte.getHistorialEstados() != null) {
            reporte.getHistorialEstados().stream().map(HistorialEstado::getUsuarioAdminId).filter(Objects::nonNull).forEach(moderadores::add);
        }
        moderadores.forEach(this::asegurarUsuario);
        repositorio.guardarReportes(List.of(reporte));
    }

    private void asegurarUsuario(String id) {
        if (id != null && !repositorio.existe("usuarios", id)) {
            buscar(id, Usuario.class).ifPresent(usuario -> repositorio.guardarUsuarios(List.of(usuario)));
        }
    }

    private void asegurarCategoria(String id) {
        if (id != null && !repositorio.existe("categorias", id)) {
            buscar(id, Categoria.class).ifPresent(categoria -> repositorio.guardarCategorias(List.of(categoria)));
        }
    }

    private void asegurarReporte(String id) {
        if (id != null && !repositorio.existe("reportes", id)) buscar(id, Reporte.class).ifPresent(this::guardarReporte);
    }

    private <T> Optional<T> buscar(String id, Class<T> tipo) {
        return Optional.ofNullable(mongoTemplate.findById(id, tipo));
    }

    private <T> List<T> delReporte(String reporteId, Class<T> tipo) {
        return mongoTemplate.find(Query.query(Criteria.where("reporteId").is(reporteId)), tipo);
    }

    private void registrarError(String mensaje, RuntimeException exception) {
        Throwable causa = NestedExceptionUtils.getMostSpecificCause(exception);
        ultimoError = causa.getMessage() == null ? causa.getClass().getSimpleName() : causa.getMessage();
        ultimoErrorEn = Instant.now();
        pendienteReconstruir = true;
        log.warn("{}: {}. Se reintentará la reconstrucción completa.", mensaje, ultimoError);
        log.debug("Detalle del error de la réplica SQL", exception);
    }

    // ------------------------------------------------------------------ estado para el panel

    /** Registros por colección en MongoDB y filas por tabla en PostgreSQL. */
    public EstadoReplicaResponse estado() {
        Map<String, Long> mongo = conteosMongo();
        Map<String, Long> sql = null;
        boolean conectada = false;
        if (habilitada()) {
            try {
                sql = repositorio.contarFilas();
                conectada = true;
            } catch (RuntimeException exception) {
                log.debug("La réplica SQL no responde", exception);
            }
        }
        Map<String, Long> filas = sql;
        List<EstadoReplicaResponse.Tabla> tablas = ReplicaSqlRepository.TABLAS.stream()
                .map(tabla -> new EstadoReplicaResponse.Tabla(tabla, mongo.getOrDefault(tabla, 0L), filas == null ? null : filas.get(tabla)))
                .toList();
        return new EstadoReplicaResponse(habilitada(), conectada, habilitada() && pendienteReconstruir, ultimaSincronizacion,
                ultimaReconstruccion, ultimoError, ultimoErrorEn, tablas);
    }

    private Map<String, Long> conteosMongo() {
        Map<String, Long> conteos = new LinkedHashMap<>();
        conteos.put("usuarios", mongoTemplate.count(new Query(), Usuario.class));
        conteos.put("categorias", mongoTemplate.count(new Query(), Categoria.class));
        conteos.put("barrios", mongoTemplate.count(new Query(), Barrio.class));
        conteos.put("calles", sumarTamanos(Barrio.class, "calles"));
        conteos.put("reportes", mongoTemplate.count(new Query(), Reporte.class));
        conteos.put("reporte_fotos", sumarTamanos(Reporte.class, "fotos"));
        conteos.put("historial_estados", sumarTamanos(Reporte.class, "historialEstados"));
        conteos.put("comentarios", mongoTemplate.count(new Query(), Comentario.class));
        conteos.put("confirmaciones", mongoTemplate.count(new Query(), Confirmacion.class));
        conteos.put("seguimientos", mongoTemplate.count(new Query(), Seguimiento.class));
        conteos.put("auditoria", mongoTemplate.count(new Query(), RegistroAuditoria.class));
        return conteos;
    }

    // Elementos embebidos en total, por ejemplo todas las calles de todos los barrios.
    private long sumarTamanos(Class<?> tipo, String arreglo) {
        List<Document> pipeline = List.of(new Document("$group", new Document("_id", null)
                .append("total", new Document("$sum", new Document("$size", new Document("$ifNull", List.of("$" + arreglo, List.of())))))));
        Document resultado = mongoTemplate.getCollection(mongoTemplate.getCollectionName(tipo)).aggregate(pipeline).first();
        return resultado == null ? 0 : ((Number) resultado.get("total")).longValue();
    }

    // Para las pruebas: espera a que el hilo de la réplica termine lo que tiene en cola.
    void esperarCola() throws Exception {
        hilo.submit(() -> { }).get();
    }
}
