package com.viactg.service;

import com.viactg.dto.ComentarioActualizarRequest;
import com.viactg.dto.ComentarioCrearRequest;
import com.viactg.exception.ForbiddenOperationException;
import com.viactg.exception.ResourceNotFoundException;
import com.viactg.model.Comentario;
import com.viactg.repository.ComentarioRepository;
import com.viactg.repository.ReporteRepository;
import com.viactg.repository.UsuarioRepository;
import com.viactg.security.LimitadorIntentos;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class ComentarioService {
    // Protección contra spam en la conversación de un reporte.
    static final int MAX_COMENTARIOS = 20;
    static final Duration VENTANA_COMENTARIOS = Duration.ofMinutes(10);

    private final ComentarioRepository comentarioRepository;
    private final ReporteRepository reporteRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final LimitadorIntentos limitador;

    public ComentarioService(ComentarioRepository comentarioRepository, ReporteRepository reporteRepository,
                             UsuarioRepository usuarioRepository, UsuarioService usuarioService, LimitadorIntentos limitador) {
        this.comentarioRepository = comentarioRepository;
        this.reporteRepository = reporteRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
        this.limitador = limitador;
    }

    public Comentario crear(String reporteId, String usuarioId, ComentarioCrearRequest request) {
        if (!reporteRepository.existsById(reporteId)) throw new ResourceNotFoundException("Reporte no encontrado: " + reporteId);
        if (!usuarioRepository.existsById(usuarioId)) throw new ResourceNotFoundException("Usuario no encontrado: " + usuarioId);
        limitador.consumir("comentarios-usuario:" + usuarioId, MAX_COMENTARIOS, VENTANA_COMENTARIOS);
        return comentarioRepository.save(Comentario.builder().reporteId(reporteId).usuarioId(usuarioId)
                .texto(request.texto().trim()).fecha(Instant.now()).editado(false).build());
    }

    public List<Comentario> listarPorReporte(String reporteId) {
        if (!reporteRepository.existsById(reporteId)) throw new ResourceNotFoundException("Reporte no encontrado: " + reporteId);
        return comentarioRepository.findByReporteIdOrderByFechaAsc(reporteId);
    }

    /** Solo su autor puede editarlo: el comentario sigue firmado con su nombre, así que nadie más debe cambiar el texto. */
    public Comentario actualizar(String comentarioId, String solicitanteId, ComentarioActualizarRequest request) {
        Comentario comentario = buscar(comentarioId);
        if (!comentario.getUsuarioId().equals(solicitanteId)) {
            throw new ForbiddenOperationException("Solo quien escribió el comentario puede editarlo");
        }
        comentario.setTexto(request.texto().trim());
        comentario.setEditado(true);
        return comentarioRepository.save(comentario);
    }

    /** El autor o un moderador/administrador (para retirar contenido inapropiado). */
    public void eliminar(String comentarioId, String solicitanteId) {
        Comentario comentario = buscar(comentarioId);
        if (!comentario.getUsuarioId().equals(solicitanteId) && !usuarioService.esAdministradorOModerador(solicitanteId)) {
            throw new ForbiddenOperationException("No tienes permiso para eliminar este comentario");
        }
        comentarioRepository.delete(comentario);
    }

    private Comentario buscar(String comentarioId) {
        return comentarioRepository.findById(comentarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Comentario no encontrado: " + comentarioId));
    }
}
