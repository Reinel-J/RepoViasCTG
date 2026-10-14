package com.viactg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.index.IndexDirection;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "reportes")
// Listados públicos y del panel: por estado y del más reciente al más antiguo.
@CompoundIndex(name = "estado_fecha", def = "{'estado': 1, 'fechaCreacion': -1}")
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
    private String direccionOsm;
    // Barrio según OpenStreetMap (neighbourhood/suburb), resuelto junto con la dirección; null si no se conoce.
    private String barrioOsm;
    private String descripcion;
    @Size(max = 3)
    @Builder.Default
    private List<String> fotos = new ArrayList<>();
    private Double latitud;
    private Double longitud;

    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoJsonPoint ubicacion;

    private EstadoReporte estado;
    private PrioridadReporte prioridad;
    @Indexed(direction = IndexDirection.DESCENDING)
    private Instant fechaCreacion;
    private Instant fechaActualizacion;
    // Último recordatorio "¿ya lo arreglaron?" enviado; null si nunca se envió.
    private Instant fechaUltimoSeguimiento;

    @Field("historialEstados")
    @Builder.Default
    private List<HistorialEstado> historialEstados = new ArrayList<>();
}
