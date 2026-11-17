package com.viactg.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Categoría embebida en un reporte (referencia extendida): el {@code id} apunta al catálogo {@code categorias} y el
 * {@code nombre} es una copia para mostrar, filtrar y agrupar sin consultar el catálogo. Si un administrador renombra
 * la categoría, {@code CategoriaService} actualiza esta copia en todos los reportes.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CategoriaReporte {

    // Sin @Field, Spring Data guardaría un "id" anidado como "_id"; así el documento queda {id, nombre}, igual que en la API.
    @Field("id")
    private String id;
    private String nombre;

    public static CategoriaReporte de(Categoria categoria) {
        return new CategoriaReporte(categoria.getId(), categoria.getNombre());
    }
}
