package com.viactg.mapper;

import com.viactg.dto.BarrioResponse;
import com.viactg.dto.CalleResponse;
import com.viactg.model.Barrio;
import com.viactg.model.Calle;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BarrioMapper {
    public BarrioResponse toResponse(Barrio barrio) {
        List<CalleResponse> calles = barrio.getCalles().stream().map(this::toCalleResponse).toList();
        return new BarrioResponse(barrio.getId(), barrio.getNombre(), barrio.getLocalidad(), calles);
    }

    public CalleResponse toCalleResponse(Calle calle) {
        return new CalleResponse(calle.getId(), calle.getNombre(), calle.getCodigoPostal());
    }
}
