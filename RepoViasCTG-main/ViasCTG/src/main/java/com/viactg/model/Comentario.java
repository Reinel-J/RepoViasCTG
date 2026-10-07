package com.viactg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "comentarios")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Comentario {

    @Id
    private String id;
    private String reporteId;
    private String usuarioId;
    private String texto;
    private Instant fecha;
    private boolean editado;
}
