package com.viactg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "reportes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reporte {

    @Id
    private String id;
    private String usuarioId;
    private String calleId;
    private String categoriaId;
    private String descripcion;
    private String fotoUrl;
    private Double latitud;
    private Double longitud;

    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoJsonPoint ubicacion;

    private EstadoReporte estado;
    private PrioridadReporte prioridad;
    private Instant fechaCreacion;
    private Instant fechaActualizacion;

    @Field("historialEstados")
    @Builder.Default
    private List<HistorialEstado> historialEstados = new ArrayList<>();
}
