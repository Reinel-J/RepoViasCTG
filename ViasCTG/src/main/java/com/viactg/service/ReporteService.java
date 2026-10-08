package com.viactg.service;

import com.viactg.dto.ReporteActualizarRequest;
import com.viactg.dto.ReporteCrearRequest;
import com.viactg.exception.BusinessRuleException;
import com.viactg.exception.ForbiddenOperationException;
import com.viactg.exception.ResourceNotFoundException;
import com.viactg.model.EstadoReporte;
import com.viactg.model.HistorialEstado;
import com.viactg.model.PrioridadReporte;
import com.viactg.model.Reporte;
import com.viactg.repository.ReporteRepository;
import com.viactg.repository.UsuarioRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ReporteService {
    private static final int MAX_FOTOS = 3;
    private static final long MAX_TAMANO_FOTO_BYTES = 5L * 1024 * 1024;
    private final ReporteRepository reporteRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final MongoTemplate mongoTemplate;
    private final GeocodingService geocodingService;
    private final Path uploadsDirectory;

    public ReporteService(ReporteRepository reporteRepository, UsuarioRepository usuarioRepository,
                          UsuarioService usuarioService, MongoTemplate mongoTemplate, GeocodingService geocodingService,
                          @Value("${app.uploads.dir:/app/uploads}") String uploadsDirectory) {
        this.reporteRepository = reporteRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
        this.mongoTemplate = mongoTemplate;
        this.geocodingService = geocodingService;
        this.uploadsDirectory = Path.of(uploadsDirectory);
    }

    public Reporte crear(ReporteCrearRequest request, String usuarioId) {
        validarReferencias(usuarioId);
        Instant ahora = Instant.now();
        return reporteRepository.save(Reporte.builder().usuarioId(usuarioId)
                .categoriaId(request.categoriaId()).direccionOsm(geocodingService.resolverDireccion(request.latitud(), request.longitud()))
                .descripcion(request.descripcion().trim())
                .latitud(request.latitud()).longitud(request.longitud())
                .ubicacion(new GeoJsonPoint(request.longitud(), request.latitud())).prioridad(PrioridadReporte.MEDIA)
                .estado(EstadoReporte.PENDIENTE).fechaCreacion(ahora).fechaActualizacion(ahora).build());
    }

    public List<Reporte> listar(EstadoReporte estado) { return estado == null ? reporteRepository.findAll() : reporteRepository.findByEstado(estado); }

    public List<Reporte> listarCercanos(double latitud, double longitud, double radioKm, EstadoReporte estado) {
        if (radioKm <= 0) {
            throw new BusinessRuleException("El radio de búsqueda debe ser mayor a cero");
        }
        GeoJsonPoint punto = new GeoJsonPoint(longitud, latitud);
        Criteria criteria = Criteria.where("ubicacion").nearSphere(punto).maxDistance(radioKm * 1000);
        if (estado != null) {
            criteria.and("estado").is(estado);
        }
        return mongoTemplate.find(Query.query(criteria), Reporte.class);
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
        reporte.setDescripcion(request.descripcion().trim());
        reporte.setLatitud(request.latitud());
        reporte.setLongitud(request.longitud());
        reporte.setUbicacion(new GeoJsonPoint(request.longitud(), request.latitud()));
        reporte.setFechaActualizacion(Instant.now());
        return reporteRepository.save(reporte);
    }

    public Reporte subirFotos(String reporteId, List<MultipartFile> archivos, String usuarioId) {
        Reporte reporte = buscarPorId(reporteId);
        if (!reporte.getUsuarioId().equals(usuarioId)) {
            throw new ForbiddenOperationException("Solo el propietario puede subir fotos al reporte");
        }
        if (archivos == null || archivos.isEmpty()) {
            throw new BusinessRuleException("Debes enviar al menos una foto");
        }
        List<String> fotosActuales = reporte.getFotos() == null ? new ArrayList<>() : new ArrayList<>(reporte.getFotos());
        if (fotosActuales.size() + archivos.size() > MAX_FOTOS) {
            throw new BusinessRuleException("Un reporte puede tener como máximo 3 fotos");
        }

        List<ArchivoValidado> archivosValidados = archivos.stream().map(this::validarFoto).toList();
        List<String> nuevasFotos = new ArrayList<>();
        try {
            Files.createDirectories(uploadsDirectory);
            for (ArchivoValidado archivo : archivosValidados) {
                String nombre = UUID.randomUUID() + archivo.extension();
                Files.write(uploadsDirectory.resolve(nombre), archivo.contenido());
                nuevasFotos.add("/uploads/" + nombre);
            }
        } catch (IOException exception) {
            throw new BusinessRuleException("No fue posible guardar las fotos");
        }

        fotosActuales.addAll(nuevasFotos);
        reporte.setFotos(fotosActuales);
        reporte.setFechaActualizacion(Instant.now());
        return reporteRepository.save(reporte);
    }

    public Reporte cambiarEstadoReporte(String reporteId, EstadoReporte nuevoEstado, String adminId, String comentario, PrioridadReporte prioridad) {
        if (!usuarioService.esAdministradorOModerador(adminId)) {
            throw new ForbiddenOperationException("Solo un administrador o moderador puede cambiar el estado");
        }
        Reporte reporte = buscarPorId(reporteId);
        validarTransicion(reporte.getEstado(), nuevoEstado);
        Instant ahora = Instant.now();
        HistorialEstado historial = new HistorialEstado(UUID.randomUUID().toString(), adminId, reporte.getEstado(), nuevoEstado, comentario, ahora);
        Query query = Query.query(Criteria.where("_id").is(reporteId).and("estado").is(reporte.getEstado()));
        Update update = new Update().set("estado", nuevoEstado).set("fechaActualizacion", ahora).push("historialEstados", historial);
        if (prioridad != null) {
            update.set("prioridad", prioridad);
        }
        Reporte actualizado = mongoTemplate.findAndModify(query, update,
                org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true), Reporte.class);
        if (actualizado == null) throw new BusinessRuleException("El reporte cambió de estado; vuelve a intentarlo");
        return actualizado;
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
}
