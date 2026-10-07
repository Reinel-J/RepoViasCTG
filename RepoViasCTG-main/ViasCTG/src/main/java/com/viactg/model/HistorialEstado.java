package com.viactg.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HistorialEstado {

    private String id;
    private String usuarioAdminId;
    private EstadoReporte estadoAnterior;
    private EstadoReporte estadoNuevo;
    private String comentarioAdmin;
    private Instant fecha;
}
