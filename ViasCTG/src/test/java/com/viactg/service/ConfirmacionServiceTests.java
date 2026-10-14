package com.viactg.service;

import com.viactg.exception.BusinessRuleException;
import com.viactg.model.Confirmacion;
import com.viactg.model.EstadoReporte;
import com.viactg.model.Reporte;
import com.viactg.repository.ConfirmacionRepository;
import com.viactg.repository.ReporteRepository;
import com.viactg.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfirmacionServiceTests {
    private final ConfirmacionRepository confirmacionRepository = mock(ConfirmacionRepository.class);
    private final ReporteRepository reporteRepository = mock(ReporteRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final ConfirmacionService service = new ConfirmacionService(confirmacionRepository, reporteRepository, usuarioRepository);

    private void reporte(EstadoReporte estado) {
        when(reporteRepository.findById("r-1")).thenReturn(Optional.of(
                Reporte.builder().id("r-1").usuarioId("autor-1").estado(estado).build()));
        when(usuarioRepository.existsById(any())).thenReturn(true);
    }

    @Test
    void otraPersonaPuedeConfirmarUnReporteActivo() {
        reporte(EstadoReporte.EN_REVISION);
        when(confirmacionRepository.save(any(Confirmacion.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        Confirmacion confirmacion = service.crear("r-1", "vecina-1");

        assertThat(confirmacion.getUsuarioId()).isEqualTo("vecina-1");
        assertThat(confirmacion.getReporteId()).isEqualTo("r-1");
    }

    @Test
    void elAutorNoPuedeConfirmarSuPropioReporte() {
        reporte(EstadoReporte.PENDIENTE);

        assertThatThrownBy(() -> service.crear("r-1", "autor-1"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("propio reporte");
        verify(confirmacionRepository, never()).save(any());
    }

    @Test
    void noSeConfirmanReportesCerrados() {
        reporte(EstadoReporte.RESUELTO);

        assertThatThrownBy(() -> service.crear("r-1", "vecina-1"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("cerrado");
        verify(confirmacionRepository, never()).save(any());
    }
}
