package com.viactg.mapper;

import com.viactg.dto.CategoriaResponse;
import com.viactg.model.Categoria;
import org.springframework.stereotype.Component;

@Component
public class CategoriaMapper {
    public CategoriaResponse toResponse(Categoria categoria) {
        return new CategoriaResponse(categoria.getId(), categoria.getNombre(), categoria.getDescripcion(),
                categoria.isActiva());
    }
}
