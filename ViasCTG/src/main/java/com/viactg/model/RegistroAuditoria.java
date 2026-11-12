package com.viactg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "auditoria")
@CompoundIndex(name = "actor_entidad", def = "{'actorId': 1, 'entidad': 1}")
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class RegistroAuditoria {
    @Id private String id;
    @Indexed(direction = org.springframework.data.mongodb.core.index.IndexDirection.DESCENDING) private Instant fecha;
    @Indexed private String actorId;
    private String actorNombre;
    private String accion;
    private String entidad;
    private String entidadId;
    private String detalle;
}
