package com.viactg.service;

import com.viactg.dto.ComentarioActualizarRequest;
import com.viactg.dto.ComentarioCrearRequest;
import com.viactg.exception.ForbiddenOperationException;
import com.viactg.exception.ResourceNotFoundException;
import com.viactg.model.Comentario;
import com.viactg.repository.ComentarioRepository;
import com.viactg.repository.ReporteRepository;
import com.viactg.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ComentarioService {
    private final ComentarioRepository comentarioRepository;
    private final ReporteRepository reporteRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;

    public ComentarioService(ComentarioRepository comentarioRepository, ReporteRepository reporteRepository,
                             UsuarioRepository usuarioRepository, UsuarioService usuarioService) {
        this.comentarioRepository = comentarioRepository;
        this.reporteRepository = reporteRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
    }

    public Comentario crear(String reporteId, String usuarioId, ComentarioCrearRequest request) {
        if (!reporteRepository.existsById(reporteId)) throw new ResourceNotFoundException("Reporte no encontrado: " + reporteId);
        if (!usuarioRepository.existsById(usuarioId)) throw new ResourceNotFoundException("Usuario no encontrado: " + usuarioId);
        return comentarioRepository.save(Comentario.builder().reporteId(reporteId).usuarioId(usuarioId)
                .texto(request.texto().trim()).fecha(Instant.now()).editado(false).build());
    }

    public List<Comentario> listarPorReporte(String reporteId) {
        if (!reporteRepository.existsById(reporteId)) throw new ResourceNotFoundException("Reporte no encontrado: " + reporteId);
        return comentarioRepository.findByReporteIdOrderByFechaAsc(reporteId);
    }

    public Comentario actualizar(String comentarioId, String solicitanteId, ComentarioActualizarRequest request) {
        Comentario comentario = comentarioRepository.findById(comentarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Comentario no encontrado: " + comentarioId));
        if (!comentario.getUsuarioId().equals(solicitanteId) && !usuarioService.esAdministradorOModerador(solicitanteId)) {
            throw new ForbiddenOperationException("No tienes permiso para editar este comentario");
        }
        comentario.setTexto(request.texto().trim());
        comentario.setEditado(true);
        return comentarioRepository.save(comentario);
    }

    public void eliminar(String comentarioId, String solicitanteId) {
        Comentario comentario = comentarioRepository.findById(comentarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Comentario no encontrado: " + comentarioId));
        if (!comentario.getUsuarioId().equals(solicitanteId) && !usuarioService.esAdministradorOModerador(solicitanteId)) {
            throw new ForbiddenOperationException("No tienes permiso para eliminar este comentario");
        }
        comentarioRepository.delete(comentario);
    }
}
