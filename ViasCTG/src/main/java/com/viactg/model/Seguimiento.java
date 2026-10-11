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

/** Respuesta de un usuario a "¿ya arreglaron este daño?". Una por usuario y reporte; puede cambiarla. */
@Document(collection = "seguimientos")
@CompoundIndex(
        name = "seguimiento_reporte_usuario_unico",
        def = "{'reporteId': 1, 'usuarioId': 1}",
        unique = true
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Seguimiento {

    @Id
    private String id;
    private String reporteId;
    private String usuarioId;
    private boolean arreglado;
    private Instant fecha;
}
