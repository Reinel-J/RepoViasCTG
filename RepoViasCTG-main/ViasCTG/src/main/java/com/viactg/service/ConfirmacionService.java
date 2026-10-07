package com.viactg.service;

import com.viactg.exception.DuplicateResourceException;
import com.viactg.exception.ResourceNotFoundException;
import com.viactg.model.Confirmacion;
import com.viactg.repository.ConfirmacionRepository;
import com.viactg.repository.ReporteRepository;
import com.viactg.repository.UsuarioRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ConfirmacionService {
    private final ConfirmacionRepository confirmacionRepository;
    private final ReporteRepository reporteRepository;
    private final UsuarioRepository usuarioRepository;

    public ConfirmacionService(ConfirmacionRepository confirmacionRepository, ReporteRepository reporteRepository,
                               UsuarioRepository usuarioRepository) {
        this.confirmacionRepository = confirmacionRepository;
        this.reporteRepository = reporteRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public Confirmacion crear(String reporteId, String usuarioId) {
        if (!reporteRepository.existsById(reporteId)) throw new ResourceNotFoundException("Reporte no encontrado: " + reporteId);
        if (!usuarioRepository.existsById(usuarioId)) throw new ResourceNotFoundException("Usuario no encontrado: " + usuarioId);
        if (confirmacionRepository.existsByReporteIdAndUsuarioId(reporteId, usuarioId)) {
            throw new DuplicateResourceException("El usuario ya confirmó este reporte");
        }
        try {
            return confirmacionRepository.save(Confirmacion.builder().reporteId(reporteId).usuarioId(usuarioId).fecha(Instant.now()).build());
        } catch (DuplicateKeyException exception) {
            throw new DuplicateResourceException("El usuario ya confirmó este reporte");
        }
    }

    public List<Confirmacion> listarPorReporte(String reporteId) {
        if (!reporteRepository.existsById(reporteId)) throw new ResourceNotFoundException("Reporte no encontrado: " + reporteId);
        return confirmacionRepository.findByReporteId(reporteId);
    }

    public void retirar(String reporteId, String usuarioId) {
        Confirmacion confirmacion = confirmacionRepository.findByReporteIdAndUsuarioId(reporteId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Confirmación no encontrada"));
        confirmacionRepository.delete(confirmacion);
    }
}
