package com.viactg.mapper;

import com.viactg.dto.ComentarioResponse;
import com.viactg.model.Comentario;
import org.springframework.stereotype.Component;

@Component
public class ComentarioMapper {
    public ComentarioResponse toResponse(Comentario comentario) {
        return new ComentarioResponse(comentario.getId(), comentario.getReporteId(), comentario.getUsuarioId(),
                comentario.getTexto(), comentario.getFecha(), comentario.isEditado());
    }
}
