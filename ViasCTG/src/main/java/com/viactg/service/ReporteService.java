package com.viactg.service;

import com.viactg.dto.ReporteActualizarRequest;
import com.viactg.dto.ReporteCrearRequest;
import com.viactg.exception.BusinessRuleException;
import com.viactg.exception.ForbiddenOperationException;
import com.viactg.exception.ResourceNotFoundException;
import com.viactg.model.Confirmacion;
import com.viactg.model.EstadoReporte;
import com.viactg.model.HistorialEstado;
import com.viactg.model.PrioridadReporte;
import com.viactg.model.Reporte;
import com.viactg.repository.ConfirmacionRepository;
import com.viactg.repository.ReporteRepository;
import com.viactg.repository.UsuarioRepository;
import com.viactg.security.LimitadorIntentos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reportes: creación, edición, fotos y cambios de estado. Toda modificación de un reporte existente es una
 * actualización parcial atómica ({@code findAndModify}/{@code updateFirst}); nunca se reescribe el documento
 * completo con {@code save()}, porque eso podría deshacer un cambio de estado (y su historial) hecho al mismo
 * tiempo por un moderador, o la dirección que guarda la geocodificación en segundo plano.
 */
@Service
public class ReporteService {
    private static final Logger log = LoggerFactory.getLogger(ReporteService.class);
    private static final int MAX_FOTOS = 3;
    private static final long MAX_TAMANO_FOTO_BYTES = 5L * 1024 * 1024;
    static final double MAX_RADIO_KM = 50;
    static final int MAX_RESULTADOS_CERCANOS = 500;
    // Protección contra spam: un ciudadano puede reportar varios daños de un recorrido, pero no cientos.
    static final int MAX_REPORTES_POR_HORA = 10;
    private static final Sort MAS_RECIENTES = Sort.by(Sort.Direction.DESC, "fechaCreacion");

    private final ReporteRepository reporteRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final MongoTemplate mongoTemplate;
    private final GeocodingService geocodingService;
    private final NotificacionService notificacionService;
    private final CategoriaService categoriaService;
    private final ConfirmacionRepository confirmacionRepository;
    private final LimitadorIntentos limitador;
    private final AreaServicio areaServicio;
    private final Path uploadsDirectory;
    // Un solo hilo: Nominatim limita a 1 petición/s y así no se bloquea la petición HTTP del usuario.
    private final ExecutorService geocodingExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread hilo = new Thread(runnable, "geocoding-direccion");
        hilo.setDaemon(true);
        return hilo;
    });

    public ReporteService(ReporteRepository reporteRepository, UsuarioRepository usuarioRepository,
                          UsuarioService usuarioService, MongoTemplate mongoTemplate, GeocodingService geocodingService,
                          NotificacionService notificacionService, CategoriaService categoriaService,
                          ConfirmacionRepository confirmacionRepository, LimitadorIntentos limitador,
                          AreaServicio areaServicio, @Value("${app.uploads.dir:uploads}") String uploadsDirectory) {
        this.reporteRepository = reporteRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
        this.mongoTemplate = mongoTemplate;
        this.geocodingService = geocodingService;
        this.notificacionService = notificacionService;
        this.categoriaService = categoriaService;
        this.confirmacionRepository = confirmacionRepository;
        this.limitador = limitador;
        this.areaServicio = areaServicio;
        this.uploadsDirectory = Path.of(uploadsDirectory);
    }

    public Reporte crear(ReporteCrearRequest request, String usuarioId) {
        validarReferencias(usuarioId);
        areaServicio.validar(request.latitud(), request.longitud());
        limitador.consumir("reportes-usuario:" + usuarioId, MAX_REPORTES_POR_HORA, Duration.ofHours(1));
        Instant ahora = Instant.now();
        Reporte guardado = reporteRepository.save(Reporte.builder().usuarioId(usuarioId)
                .categoriaId(categoriaService.normalizarNombre(request.categoriaId()))
                .descripcion(request.descripcion().trim())
                .latitud(request.latitud()).longitud(request.longitud())
                .ubicacion(new GeoJsonPoint(request.longitud(), request.latitud())).prioridad(PrioridadReporte.MEDIA)
                .estado(EstadoReporte.PENDIENTE).fechaCreacion(ahora).fechaActualizacion(ahora).build());
        resolverDireccionAsync(guardado.getId(), guardado.getLatitud(), guardado.getLongitud());
        return guardado;
    }

    private void resolverDireccionAsync(String reporteId, double latitud, double longitud) {
        geocodingExecutor.execute(() -> {
            try {
                GeocodingService.DireccionOsm direccion = geocodingService.resolverEnSegundoPlano(latitud, longitud);
                if (direccion == null || direccion.vacia()) return;
                // Solo si el reporte sigue en esas coordenadas: si lo movieron, la nueva ubicación pide su propia dirección.
                Query query = Query.query(Criteria.where("_id").is(reporteId).and("latitud").is(latitud).and("longitud").is(longitud));
                mongoTemplate.updateFirst(query, new Update().set("direccionOsm", direccion.direccion())
                        .set("barrioOsm", direccion.barrio()), Reporte.class);
            } catch (RuntimeException exception) {
                log.warn("No fue posible resolver la dirección del reporte {}", reporteId, exception);
            }
        });
    }

    /** Del más reciente al más antiguo; {@code limite} null devuelve todos. */
    public List<Reporte> listar(EstadoReporte estado, Integer limite) {
        Query query = new Query().with(MAS_RECIENTES);
        if (estado != null) query.addCriteria(Criteria.where("estado").is(estado));
        if (limite != null) query.limit(limite);
        return mongoTemplate.find(query, Reporte.class);
    }

    public List<Reporte> listar(EstadoReporte estado) {
        return listar(estado, null);
    }

    /** Reportes creados por el usuario, del más reciente al más antiguo; {@code estado} null devuelve todos. */
    public List<Reporte> listarDeUsuario(String usuarioId, EstadoReporte estado) {
        Query query = Query.query(Criteria.where("usuarioId").is(usuarioId)).with(MAS_RECIENTES);
        if (estado != null) query.addCriteria(Criteria.where("estado").is(estado));
        return mongoTemplate.find(query, Reporte.class);
    }

    /** Cifras de la portada con conteos en la base de datos. */
    public Resumen calcularResumen(Instant ahora) {
        Map<EstadoReporte, Long> porEstado = new EnumMap<>(EstadoReporte.class);
        for (EstadoReporte estado : EstadoReporte.values()) {
            porEstado.put(estado, reporteRepository.countByEstado(estado));
        }
        long total = porEstado.values().stream().mapToLong(Long::longValue).sum();
        long ultimas24Horas = reporteRepository.countByFechaCreacionGreaterThanEqual(ahora.minus(Duration.ofDays(1)));
        long barrios = mongoTemplate.findDistinct(new Query(Criteria.where("barrioOsm").ne(null)), "barrioOsm",
                Reporte.class, String.class).size();
        return new Resumen(total, porEstado, ultimas24Horas, barrios);
    }

    public List<Reporte> listarCercanos(double latitud, double longitud, double radioKm, EstadoReporte estado) {
        if (radioKm <= 0) {
            throw new BusinessRuleException("El radio de búsqueda debe ser mayor a cero");
        }
        if (radioKm > MAX_RADIO_KM) {
            throw new BusinessRuleException("El radio de búsqueda no puede superar " + (int) MAX_RADIO_KM + " km");
        }
        GeoJsonPoint punto = new GeoJsonPoint(longitud, latitud);
        Criteria criteria = Criteria.where("ubicacion").nearSphere(punto).maxDistance(radioKm * 1000);
        if (estado != null) {
            criteria.and("estado").is(estado);
        }
        return mongoTemplate.find(Query.query(criteria).limit(MAX_RESULTADOS_CERCANOS), Reporte.class);
    }

    public Reporte buscarPorId(String id) {
        return reporteRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Reporte no encontrado: " + id));
    }

    public Reporte actualizar(String reporteId, ReporteActualizarRequest request, String solicitanteId) {
        Reporte reporte = buscarPorId(reporteId);
        validarPropietarioOPrivilegiado(reporte, solicitanteId);
        if (reporte.getEstado() != EstadoReporte.PENDIENTE) {
            throw new BusinessRuleException("Solo se pueden editar reportes pendientes");
        }
        areaServicio.validar(request.latitud(), request.longitud());
        // Se comparan valores, no referencias: latitud y longitud son Double.
        boolean cambioUbicacion = !Objects.equals(reporte.getLatitud(), request.latitud())
                || !Objects.equals(reporte.getLongitud(), request.longitud());
        Update update = new Update().set("descripcion", request.descripcion().trim()).set("fechaActualizacion", Instant.now());
        if (cambioUbicacion) {
            update.set("latitud", request.latitud()).set("longitud", request.longitud())
                    .set("ubicacion", new GeoJsonPoint(request.longitud(), request.latitud()))
                    .unset("direccionOsm").unset("barrioOsm");
        }
        // El filtro por estado evita pisar un cambio de estado que un moderador haga entre la lectura y la escritura.
        Reporte actualizado = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(reporteId).and("estado").is(EstadoReporte.PENDIENTE)),
                update, FindAndModifyOptions.options().returnNew(true), Reporte.class);
        if (actualizado == null) {
            throw new BusinessRuleException("Solo se pueden editar reportes pendientes");
        }
        if (cambioUbicacion) {
            resolverDireccionAsync(actualizado.getId(), request.latitud(), request.longitud());
        }
        return actualizado;
    }

    public Reporte subirFotos(String reporteId, List<MultipartFile> archivos, String usuarioId) {
        Reporte reporte = buscarPorId(reporteId);
        if (!reporte.getUsuarioId().equals(usuarioId)) {
            throw new ForbiddenOperationException("Solo el propietario puede subir fotos al reporte");
        }
        if (archivos == null || archivos.isEmpty()) {
            throw new BusinessRuleException("Debes enviar al menos una foto");
        }
        int fotosActuales = reporte.getFotos() == null ? 0 : reporte.getFotos().size();
        if (fotosActuales + archivos.size() > MAX_FOTOS) {
            throw new BusinessRuleException("Un reporte puede tener como máximo 3 fotos");
        }

        List<ArchivoValidado> archivosValidados = archivos.stream().map(this::validarFoto).toList();
        List<Path> escritos = new ArrayList<>();
        List<String> nuevasFotos = new ArrayList<>();
        try {
            Files.createDirectories(uploadsDirectory);
            for (ArchivoValidado archivo : archivosValidados) {
                String nombre = UUID.randomUUID() + archivo.extension();
                Path destino = uploadsDirectory.resolve(nombre);
                Files.write(destino, archivo.contenido());
                escritos.add(destino);
                nuevasFotos.add("/uploads/" + nombre);
            }
        } catch (IOException exception) {
            borrarArchivos(escritos);
            log.error("No fue posible guardar las fotos del reporte {} en {}", reporteId, uploadsDirectory.toAbsolutePath(), exception);
            throw new BusinessRuleException("No fue posible guardar las fotos");
        }

        // Atómico: solo se agregan si el arreglo todavía tiene cupo (el índice MAX - n no existe), así dos subidas
        // simultáneas no superan el máximo ni se pisan entre sí.
        Query query = Query.query(Criteria.where("_id").is(reporteId).and("usuarioId").is(usuarioId)
                .and("fotos." + (MAX_FOTOS - nuevasFotos.size())).exists(false));
        Update update = new Update().push("fotos").each(nuevasFotos.toArray()).set("fechaActualizacion", Instant.now());
        Reporte actualizado;
        try {
            actualizado = mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), Reporte.class);
        } catch (RuntimeException exception) {
            borrarArchivos(escritos);
            throw exception;
        }
        if (actualizado == null) {
            borrarArchivos(escritos);
            throw new BusinessRuleException("Un reporte puede tener como máximo 3 fotos");
        }
        return actualizado;
    }

    private void borrarArchivos(List<Path> archivos) {
        for (Path archivo : archivos) {
            try {
                Files.deleteIfExists(archivo);
            } catch (IOException exception) {
                log.warn("No fue posible borrar la foto huérfana {}", archivo, exception);
            }
        }
    }

    public Reporte cambiarEstadoReporte(String reporteId, EstadoReporte nuevoEstado, String adminId, String comentario, PrioridadReporte prioridad) {
        if (!usuarioService.esAdministradorOModerador(adminId)) {
            throw new ForbiddenOperationException("Solo un administrador o moderador puede cambiar el estado");
        }
        Reporte reporte = buscarPorId(reporteId);
        validarTransicion(reporte.getEstado(), nuevoEstado);
        return aplicarCambioEstado(reporte, nuevoEstado, adminId, comentario, prioridad);
    }

    /**
     * Cierre automático por los votos «¿ya lo arreglaron?» (lo decide SeguimientoService). Solo desde EN_PROCESO; en el
     * historial queda sin moderador ({@code usuarioAdminId} null) y con el comentario que explica los votos.
     */
    public Reporte cerrarPorLaComunidad(String reporteId, String comentario) {
        Reporte reporte = buscarPorId(reporteId);
        if (reporte.getEstado() != EstadoReporte.EN_PROCESO) {
            throw new BusinessRuleException("Solo un reporte en proceso puede cerrarse por los votos de la comunidad");
        }
        return aplicarCambioEstado(reporte, EstadoReporte.RESUELTO, null, comentario, null);
    }

    // Filtra por el estado leído: si otro cambio llegó antes, no se pisa y se responde 409.
    private Reporte aplicarCambioEstado(Reporte reporte, EstadoReporte nuevoEstado, String adminId, String comentario, PrioridadReporte prioridad) {
        Instant ahora = Instant.now();
        String comentarioLimpio = comentario == null || comentario.isBlank() ? null : comentario.trim();
        HistorialEstado historial = new HistorialEstado(UUID.randomUUID().toString(), adminId, reporte.getEstado(), nuevoEstado, comentarioLimpio, ahora);
        Query query = Query.query(Criteria.where("_id").is(reporte.getId()).and("estado").is(reporte.getEstado()));
        // Una propuesta de cierre por la comunidad solo tiene sentido mientras el reporte sigue en proceso.
        Update update = new Update().set("estado", nuevoEstado).set("fechaActualizacion", ahora).push("historialEstados", historial)
                .unset("cierreComunitarioDesde");
        if (prioridad != null) {
            update.set("prioridad", prioridad);
        }
        Reporte actualizado = mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), Reporte.class);
        if (actualizado == null) throw new BusinessRuleException("El reporte cambió de estado; vuelve a intentarlo");
        notificarCambioEstado(actualizado, nuevoEstado);
        return actualizado;
    }

    private void notificarCambioEstado(Reporte reporte, EstadoReporte nuevoEstado) {
        try {
            notificacionService.crear(reporte.getUsuarioId(), reporte.getId(),
                    "Tu reporte \"" + resumen(reporte.getDescripcion()) + "\" cambió a " + etiqueta(nuevoEstado));
        } catch (RuntimeException exception) {
            log.warn("No fue posible crear la notificación del reporte {}", reporte.getId(), exception);
        }
        if (!nuevoEstado.esActivo()) {
            notificarConfirmantes(reporte, nuevoEstado);
        }
    }

    // Al cerrarse el caso también se avisa a quienes confirmaron el daño: también les importa el resultado.
    private void notificarConfirmantes(Reporte reporte, EstadoReporte nuevoEstado) {
        try {
            String mensaje = "El daño que confirmaste (\"" + resumen(reporte.getDescripcion()) + "\") quedó "
                    + etiqueta(nuevoEstado);
            confirmacionRepository.findByReporteId(reporte.getId()).stream()
                    .map(Confirmacion::getUsuarioId)
                    .filter(usuarioId -> usuarioId != null && !usuarioId.equals(reporte.getUsuarioId()))
                    .distinct()
                    .forEach(usuarioId -> notificacionService.crear(usuarioId, reporte.getId(), mensaje));
        } catch (RuntimeException exception) {
            log.warn("No fue posible notificar a quienes confirmaron el reporte {}", reporte.getId(), exception);
        }
    }

    private String etiqueta(EstadoReporte estado) {
        return switch (estado) {
            case PENDIENTE -> "pendiente";
            case EN_REVISION -> "en revisión";
            case EN_PROCESO -> "en proceso";
            case RESUELTO -> "resuelto";
            case RECHAZADO -> "rechazado";
        };
    }

    private String resumen(String descripcion) {
        if (descripcion == null) return "";
        return descripcion.length() <= 60 ? descripcion : descripcion.substring(0, 57) + "...";
    }

    public List<HistorialEstado> historial(String reporteId) { return buscarPorId(reporteId).getHistorialEstados(); }

    private void validarReferencias(String usuarioId) {
        if (!usuarioRepository.existsById(usuarioId)) throw new ResourceNotFoundException("Usuario no encontrado: " + usuarioId);
    }

    private ArchivoValidado validarFoto(MultipartFile archivo) {
        if (archivo.isEmpty()) {
            throw new BusinessRuleException("No se permiten archivos vacíos");
        }
        if (archivo.getSize() > MAX_TAMANO_FOTO_BYTES) {
            throw new BusinessRuleException("Cada foto puede pesar como máximo 5 MB");
        }
        try {
            byte[] contenido = archivo.getBytes();
            TipoImagen tipo = detectarTipoImagen(contenido);
            String extension = extensionOriginal(archivo.getOriginalFilename());
            if (tipo == null || !tipo.aceptaExtension(extension)) {
                throw new BusinessRuleException("Solo se permiten imágenes JPEG, PNG o WEBP válidas");
            }
            return new ArchivoValidado(contenido, extension);
        } catch (IOException exception) {
            throw new BusinessRuleException("No fue posible leer una de las fotos");
        }
    }

    private TipoImagen detectarTipoImagen(byte[] contenido) {
        if (contenido.length >= 3 && (contenido[0] & 0xFF) == 0xFF && (contenido[1] & 0xFF) == 0xD8 && (contenido[2] & 0xFF) == 0xFF) {
            return TipoImagen.JPEG;
        }
        if (contenido.length >= 8 && (contenido[0] & 0xFF) == 0x89 && contenido[1] == 0x50 && contenido[2] == 0x4E
                && contenido[3] == 0x47 && contenido[4] == 0x0D && contenido[5] == 0x0A && contenido[6] == 0x1A && contenido[7] == 0x0A) {
            return TipoImagen.PNG;
        }
        if (contenido.length >= 12 && contenido[0] == 'R' && contenido[1] == 'I' && contenido[2] == 'F' && contenido[3] == 'F'
                && contenido[8] == 'W' && contenido[9] == 'E' && contenido[10] == 'B' && contenido[11] == 'P') {
            return TipoImagen.WEBP;
        }
        return null;
    }

    private String extensionOriginal(String nombreOriginal) {
        if (nombreOriginal == null) {
            throw new BusinessRuleException("La foto debe conservar una extensión válida");
        }
        int ultimoPunto = nombreOriginal.lastIndexOf('.');
        if (ultimoPunto < 0 || ultimoPunto == nombreOriginal.length() - 1) {
            throw new BusinessRuleException("La foto debe conservar una extensión válida");
        }
        return nombreOriginal.substring(ultimoPunto).toLowerCase(Locale.ROOT);
    }

    private record ArchivoValidado(byte[] contenido, String extension) {
    }

    private enum TipoImagen {
        JPEG(List.of(".jpg", ".jpeg")), PNG(List.of(".png")), WEBP(List.of(".webp"));

        private final List<String> extensiones;

        TipoImagen(List<String> extensiones) {
            this.extensiones = extensiones;
        }

        boolean aceptaExtension(String extension) {
            return extensiones.contains(extension);
        }
    }

    private void validarPropietarioOPrivilegiado(Reporte reporte, String usuarioId) {
        if (!reporte.getUsuarioId().equals(usuarioId) && !usuarioService.esAdministradorOModerador(usuarioId)) {
            throw new ForbiddenOperationException("No tienes permiso sobre este reporte");
        }
    }

    private void validarTransicion(EstadoReporte actual, EstadoReporte nuevo) {
        boolean valida = switch (actual) {
            case PENDIENTE -> nuevo == EstadoReporte.EN_REVISION || nuevo == EstadoReporte.RECHAZADO;
            case EN_REVISION -> nuevo == EstadoReporte.EN_PROCESO || nuevo == EstadoReporte.RECHAZADO;
            case EN_PROCESO -> nuevo == EstadoReporte.RESUELTO || nuevo == EstadoReporte.RECHAZADO;
            case RESUELTO, RECHAZADO -> false;
        };
        if (!valida) throw new BusinessRuleException("Transición de estado no permitida: " + actual + " a " + nuevo);
    }

    /** Cifras públicas de la portada. */
    public record Resumen(long total, Map<EstadoReporte, Long> porEstado, long creadosUltimas24Horas, long barrios) {
    }
}
