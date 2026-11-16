package com.viactg.service;

import com.mongodb.client.result.UpdateResult;
import com.viactg.dto.CategoriaRequest;
import com.viactg.exception.BusinessRuleException;
import com.viactg.exception.DuplicateResourceException;
import com.viactg.exception.ResourceNotFoundException;
import com.viactg.model.Categoria;
import com.viactg.model.CategoriaReporte;
import com.viactg.model.Reporte;
import com.viactg.repository.CategoriaRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoriaServiceTests {
    private final CategoriaRepository categoriaRepository = mock(CategoriaRepository.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final AuditoriaService auditoriaService = mock(AuditoriaService.class);
    private final CategoriaService service = new CategoriaService(categoriaRepository, mongoTemplate, auditoriaService);

    private void catalogo(String... nombres) {
        when(categoriaRepository.findAll()).thenReturn(Arrays.stream(nombres)
                .map(nombre -> Categoria.builder().id("id-" + nombre).nombre(nombre).activa(true).build()).toList());
    }

    private static Categoria categoria(String id, String nombre, boolean activa) {
        return Categoria.builder().id(id).nombre(nombre).descripcion("Descripción").activa(activa).build();
    }

    @Test
    void buscarPorNombreIgnoraTildesMayusculasYEspacios() {
        catalogo("Hueco", "Inundación", "Vía sin pavimentar");

        assertThat(service.buscarPorNombre("  inundacion ")).map(Categoria::getNombre).contains("Inundación");
        assertThat(service.buscarPorNombre("VIA   SIN pavimentar")).map(Categoria::getId).contains("id-Vía sin pavimentar");
        assertThat(service.buscarPorNombre("Semáforo dañado")).isEmpty();
    }

    @Test
    void noSePuedenCrearCategoriasDuplicadas() {
        catalogo("Inundación");

        assertThatThrownBy(() -> service.crear(new CategoriaRequest("inundacion", null)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(categoriaRepository, never()).save(any());
    }

    @Test
    void laClaveDeComparacionIgnoraTildesYMayusculas() {
        assertThat(CategoriaService.claveComparacion(" Daño  en Alcantarillado ")).isEqualTo("dano en alcantarillado");
        assertThat(List.of(CategoriaService.claveComparacion("Hueco"))).containsExactly("hueco");
    }

    @Test
    void paraReporteDevuelveElIdYElNombreDeUnaCategoriaActiva() {
        when(categoriaRepository.findById("c1")).thenReturn(Optional.of(categoria("c1", "Hueco", true)));

        CategoriaReporte embebida = service.paraReporte(" c1 ");

        assertThat(embebida.getId()).isEqualTo("c1");
        assertThat(embebida.getNombre()).isEqualTo("Hueco");
    }

    @Test
    void paraReporteRechazaUnaCategoriaInexistente() {
        when(categoriaRepository.findById("nada")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.paraReporte("nada")).isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("no existe");
    }

    @Test
    void paraReporteRechazaUnaCategoriaDesactivada() {
        when(categoriaRepository.findById("c1")).thenReturn(Optional.of(categoria("c1", "Bache", false)));

        assertThatThrownBy(() -> service.paraReporte("c1")).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("«Bache» ya no está disponible");
    }

    @Test
    void renombrarActualizaElCatalogoDeFormaAtomicaYPropagaElNombreASusReportes() {
        catalogo("Hueco");
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Categoria.class)))
                .thenReturn(categoria("id-Hueco", "Hueco", true));
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(Reporte.class)))
                .thenReturn(UpdateResult.acknowledged(5, 5L, null));

        Categoria actualizada = service.actualizar("id-Hueco", new CategoriaRequest("Bache", "Hueco o bache"), "admin");

        assertThat(actualizada.getNombre()).isEqualTo("Bache");
        assertThat(actualizada.isActiva()).isTrue();
        ArgumentCaptor<Query> consulta = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> cambio = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateMulti(consulta.capture(), cambio.capture(), eq(Reporte.class));
        assertThat(consulta.getValue().getQueryObject()).isEqualTo(new Document("categoria.id", "id-Hueco"));
        assertThat(cambio.getValue().getUpdateObject()).isEqualTo(new Document("$set", new Document("categoria.nombre", "Bache")));
        verify(categoriaRepository, never()).save(any());
        verify(auditoriaService).registrar("admin", "CATEGORIA_EDITADA", "CATEGORIA", "id-Hueco",
                "Hueco → Bache (5 reporte(s) actualizados)");
    }

    @Test
    void editarSoloLaDescripcionNoTocaLosReportes() {
        catalogo("Hueco");
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Categoria.class)))
                .thenReturn(categoria("id-Hueco", "Hueco", true));

        service.actualizar("id-Hueco", new CategoriaRequest("Hueco", "Otra descripción"), "admin");

        verify(mongoTemplate, never()).updateMulti(any(Query.class), any(Update.class), eq(Reporte.class));
        verify(auditoriaService).registrar("admin", "CATEGORIA_EDITADA", "CATEGORIA", "id-Hueco", "Hueco");
    }

    @Test
    void editarUnaCategoriaInexistenteDevuelve404() {
        catalogo();

        assertThatThrownBy(() -> service.actualizar("nada", new CategoriaRequest("Bache", null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cambiarEstadoEsAtomicoYRegistraLaAccionDeAuditoria() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Categoria.class)))
                .thenReturn(categoria("c1", "Hueco", false));

        Categoria actualizada = service.cambiarEstado("c1", false, "admin");

        assertThat(actualizada.isActiva()).isFalse();
        ArgumentCaptor<Update> cambio = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(any(Query.class), cambio.capture(), any(FindAndModifyOptions.class), eq(Categoria.class));
        assertThat(cambio.getValue().getUpdateObject()).isEqualTo(new Document("$set", new Document("activa", false)));
        verify(categoriaRepository, never()).save(any());
        verify(auditoriaService).registrar("admin", "CATEGORIA_DESACTIVADA", "CATEGORIA", "c1", "Hueco");
    }

    @Test
    void asegurarOtroDevuelveLaExistenteSinCrearOtra() {
        catalogo("Hueco", "otro");

        assertThat(service.asegurarOtro().getNombre()).isEqualTo("otro");
        verify(categoriaRepository, never()).save(any());
    }

    @Test
    void asegurarOtroLaCreaActivaSiFalta() {
        catalogo("Hueco");
        when(categoriaRepository.save(any(Categoria.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        Categoria otro = service.asegurarOtro();

        assertThat(otro.getNombre()).isEqualTo(CategoriaService.NOMBRE_OTRO);
        assertThat(otro.isActiva()).isTrue();
    }
}
