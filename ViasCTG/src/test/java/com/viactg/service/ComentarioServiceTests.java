package com.viactg.service;

import com.viactg.dto.ComentarioActualizarRequest;
import com.viactg.dto.ComentarioCrearRequest;
import com.viactg.exception.ForbiddenOperationException;
import com.viactg.exception.TooManyRequestsException;
import com.viactg.model.Comentario;
import com.viactg.repository.ComentarioRepository;
import com.viactg.repository.ReporteRepository;
import com.viactg.repository.UsuarioRepository;
import com.viactg.security.LimitadorIntentos;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComentarioServiceTests {
    private final ComentarioRepository comentarioRepository = mock(ComentarioRepository.class);
    private final ReporteRepository reporteRepository = mock(ReporteRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final UsuarioService usuarioService = mock(UsuarioService.class);
    private final ComentarioService service = new ComentarioService(comentarioRepository, reporteRepository, usuarioRepository,
            usuarioService, new LimitadorIntentos());

    private Comentario comentarioDe(String autor) {
        return Comentario.builder().id("c-1").reporteId("r-1").usuarioId(autor).texto("Sigue igual").build();
    }

    @Test
    void unModeradorNoPuedeReescribirElComentarioDeOtraPersona() {
        when(comentarioRepository.findById("c-1")).thenReturn(Optional.of(comentarioDe("vecina-1")));
        when(usuarioService.esAdministradorOModerador("moderador-1")).thenReturn(true);

        assertThatThrownBy(() -> service.actualizar("c-1", "moderador-1", new ComentarioActualizarRequest("Texto cambiado")))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(comentarioRepository, never()).save(any());
    }

    @Test
    void unModeradorSiPuedeEliminarUnComentarioInapropiado() {
        Comentario comentario = comentarioDe("vecina-1");
        when(comentarioRepository.findById("c-1")).thenReturn(Optional.of(comentario));
        when(usuarioService.esAdministradorOModerador("moderador-1")).thenReturn(true);

        service.eliminar("c-1", "moderador-1");

        verify(comentarioRepository).delete(comentario);
    }

    @Test
    void limitaLaCantidadDeComentariosSeguidos() {
        when(reporteRepository.existsById(anyString())).thenReturn(true);
        when(usuarioRepository.existsById(anyString())).thenReturn(true);
        when(comentarioRepository.save(any(Comentario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        ComentarioCrearRequest request = new ComentarioCrearRequest("Confirmo, sigue peligroso");

        for (int i = 0; i < ComentarioService.MAX_COMENTARIOS; i++) service.crear("r-1", "u-1", request);

        assertThatThrownBy(() -> service.crear("r-1", "u-1", request)).isInstanceOf(TooManyRequestsException.class);
    }
}
