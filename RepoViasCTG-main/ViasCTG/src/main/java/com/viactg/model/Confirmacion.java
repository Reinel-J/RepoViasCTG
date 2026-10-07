package com.viactg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "confirmaciones")
@CompoundIndex(
        name = "reporte_usuario_unico",
        def = "{'reporteId': 1, 'usuarioId': 1}",
        unique = true
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Confirmacion {

    @Id
    private String id;
    private String reporteId;
    private String usuarioId;
    private Instant fecha;
}
