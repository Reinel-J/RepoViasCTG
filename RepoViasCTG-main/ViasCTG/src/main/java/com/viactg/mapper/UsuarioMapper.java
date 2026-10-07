package com.viactg.mapper;

import com.viactg.dto.UsuarioResponse;
import com.viactg.model.Usuario;
import org.springframework.stereotype.Component;

@Component
public class UsuarioMapper {
    public UsuarioResponse toResponse(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getEmail(),
                usuario.getTelefono(), usuario.getRol(), usuario.isActivo(), usuario.getFechaRegistro());
    }
}
