package com.viactg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "barrios")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Barrio {

    @Id
    private String id;
    private String nombre;
    private String localidad;

    @Builder.Default
    private List<Calle> calles = new ArrayList<>();
}
