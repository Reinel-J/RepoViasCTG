package com.viactg.service;

import com.viactg.dto.ReporteActualizarRequest;
import com.viactg.dto.ReporteCrearRequest;
import com.viactg.exception.BusinessRuleException;
import com.viactg.exception.ForbiddenOperationException;
import com.viactg.exception.ResourceNotFoundException;
import com.viactg.model.Categoria;
import com.viactg.model.EstadoReporte;
import com.viactg.model.HistorialEstado;
import com.viactg.model.Reporte;
import com.viactg.repository.BarrioRepository;
import com.viactg.repository.CategoriaRepository;
import com.viactg.repository.ReporteRepository;
import com.viactg.repository.UsuarioRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ReporteService {
    private final ReporteRepository reporteRepository;
    private final UsuarioRepository usuarioRepository;
    private final BarrioRepository barrioRepository;
    private final CategoriaRepository categoriaRepository;
    private final UsuarioService usuarioService;
    private final MongoTemplate mongoTemplate;

    public ReporteService(ReporteRepository reporteRepository, UsuarioRepository usuarioRepository,
                          BarrioRepository barrioRepository, CategoriaRepository categoriaRepository,
                          UsuarioService usuarioService, MongoTemplate mongoTemplate) {
        this.reporteRepository = reporteRepository;
        this.usuarioRepository = usuarioRepository;
        this.barrioRepository = barrioRepository;
        this.categoriaRepository = categoriaRepository;
        this.usuarioService = usuarioService;
        this.mongoTemplate = mongoTemplate;
    }

    public Reporte crear(ReporteCrearRequest request, String usuarioId) {
        validarReferencias(usuarioId, request.calleId(), request.categoriaId());
        Instant ahora = Instant.now();
        return reporteRepository.save(Reporte.builder().usuarioId(usuarioId).calleId(request.calleId())
                .categoriaId(request.categoriaId()).descripcion(request.descripcion().trim()).fotoUrl(request.fotoUrl())
                .latitud(request.latitud()).longitud(request.longitud())
                .ubicacion(new GeoJsonPoint(request.longitud(), request.latitud())).prioridad(request.prioridad())
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
        reporte.setFotoUrl(request.fotoUrl());
        reporte.setLatitud(request.latitud());
        reporte.setLongitud(request.longitud());
        reporte.setUbicacion(new GeoJsonPoint(request.longitud(), request.latitud()));
        reporte.setPrioridad(request.prioridad());
        reporte.setFechaActualizacion(Instant.now());
        return reporteRepository.save(reporte);
    }

    public Reporte cambiarEstadoReporte(String reporteId, EstadoReporte nuevoEstado, String adminId, String comentario) {
        if (!usuarioService.esAdministradorOModerador(adminId)) {
            throw new ForbiddenOperationException("Solo un administrador o moderador puede cambiar el estado");
        }
        Reporte reporte = buscarPorId(reporteId);
        validarTransicion(reporte.getEstado(), nuevoEstado);
        Instant ahora = Instant.now();
        HistorialEstado historial = new HistorialEstado(UUID.randomUUID().toString(), adminId, reporte.getEstado(), nuevoEstado, comentario, ahora);
        Query query = Query.query(Criteria.where("_id").is(reporteId).and("estado").is(reporte.getEstado()));
        Update update = new Update().set("estado", nuevoEstado).set("fechaActualizacion", ahora).push("historialEstados", historial);
        Reporte actualizado = mongoTemplate.findAndModify(query, update,
                org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true), Reporte.class);
        if (actualizado == null) throw new BusinessRuleException("El reporte cambió de estado; vuelve a intentarlo");
        return actualizado;
    }

    public List<HistorialEstado> historial(String reporteId) { return buscarPorId(reporteId).getHistorialEstados(); }

    private void validarReferencias(String usuarioId, String calleId, String categoriaId) {
        if (!usuarioRepository.existsById(usuarioId)) throw new ResourceNotFoundException("Usuario no encontrado: " + usuarioId);
        if (barrioRepository.findByCallesId(calleId).isEmpty()) throw new ResourceNotFoundException("Calle no encontrada: " + calleId);
        Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada: " + categoriaId));
        if (!categoria.isActiva()) throw new BusinessRuleException("La categoría indicada está inactiva");
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
