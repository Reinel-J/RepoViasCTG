package com.viactg.service;

import com.viactg.dto.BarrioRequest;
import com.viactg.dto.CalleRequest;
import com.viactg.exception.DuplicateResourceException;
import com.viactg.exception.InvalidRequestException;
import com.viactg.exception.ResourceNotFoundException;
import com.viactg.model.Barrio;
import com.viactg.model.Calle;
import com.viactg.repository.BarrioRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.ArrayList;
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

class BarrioServiceTests {
    private final BarrioRepository barrioRepository = mock(BarrioRepository.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final BarrioService service = new BarrioService(barrioRepository, mongoTemplate);

    private Barrio barrioExistente(Calle... calles) {
        Barrio barrio = Barrio.builder().id("b1").nombre("Getsemaní").localidad("Histórica")
                .calles(new ArrayList<>(List.of(calles))).build();
        when(barrioRepository.findById("b1")).thenReturn(Optional.of(barrio));
        return barrio;
    }

    /** Lo que Mongo devolvería tras aplicar la actualización (el contenido no importa: se verifica el Update). */
    private void findAndModifyDevuelve(Barrio barrio) {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Barrio.class)))
                .thenReturn(barrio);
    }

    private Document cambiosEnviados() {
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(any(Query.class), update.capture(), any(FindAndModifyOptions.class), eq(Barrio.class));
        return update.getValue().getUpdateObject();
    }

    @Test
    void crearRecortaEspaciosYAsignaUnIdACadaCalle() {
        when(barrioRepository.save(any(Barrio.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        BarrioRequest request = new BarrioRequest("  Getsemaní ", " Histórica  ",
                List.of(new CalleRequest(" Calle de la Sierpe ", " 130001 ")));

        Barrio creado = service.crear(request);

        assertThat(creado.getNombre()).isEqualTo("Getsemaní");
        assertThat(creado.getLocalidad()).isEqualTo("Histórica");
        assertThat(creado.getCalles()).hasSize(1);
        assertThat(creado.getCalles().get(0).getNombre()).isEqualTo("Calle de la Sierpe");
        assertThat(creado.getCalles().get(0).getCodigoPostal()).isEqualTo("130001");
        assertThat(creado.getCalles().get(0).getId()).isNotBlank();
    }

    @Test
    void crearSinCallesDejaUnaListaVacia() {
        when(barrioRepository.save(any(Barrio.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        Barrio creado = service.crear(new BarrioRequest("Bocagrande", "Turística", null));

        assertThat(creado.getCalles()).isEmpty();
    }

    @Test
    void buscarPorIdFallaSiElBarrioNoExiste() {
        when(barrioRepository.findById("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId("x"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("x");
    }

    @Test
    void crearGuardaLaClaveDeComparacionDelNombre() {
        when(barrioRepository.save(any(Barrio.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        Barrio creado = service.crear(new BarrioRequest("San  Diego", "Histórica", null));

        assertThat(creado.getNombreClave()).isEqualTo("san diego");
    }

    @Test
    void crearRechazaUnNombreRepetidoAunqueCambienTildesMayusculasOEspacios() {
        when(barrioRepository.findAll()).thenReturn(List.of(Barrio.builder().id("b1").nombre("Getsemaní").build()));

        assertThatThrownBy(() -> service.crear(new BarrioRequest("  GETSEMANI ", "Histórica", null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Ya existe el barrio «Getsemaní»");
        verify(barrioRepository, never()).save(any(Barrio.class));
    }

    @Test
    void actualizarCambiaNombreYLocalidadSinTocarLasCalles() {
        Barrio esperado = Barrio.builder().id("b1").nombre("Manga").calles(List.of(new Calle("c1", "Calle Real", "130001"))).build();
        findAndModifyDevuelve(esperado);

        Barrio actualizado = service.actualizar("b1", new BarrioRequest(" Manga ", " Industrial ", null));

        assertThat(actualizado).isSameAs(esperado);
        Document cambios = cambiosEnviados();
        // Solo $set de los dos campos: el formulario del panel no envía calles y antes eso las borraba todas.
        assertThat(cambios).containsOnlyKeys("$set");
        assertThat(cambios.get("$set", Document.class)).containsOnlyKeys("nombre", "nombreClave", "localidad")
                .containsEntry("nombre", "Manga").containsEntry("nombreClave", "manga").containsEntry("localidad", "Industrial");
        verify(barrioRepository, never()).save(any(Barrio.class));
    }

    @Test
    void actualizarConCallesEnElCuerpoSeRechazaYNoModificaNada() {
        assertThatThrownBy(() -> service.actualizar("b1", new BarrioRequest("Manga", "Histórica",
                List.of(new CalleRequest("Calle Nueva", "130002")))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("/calles");
        assertThatThrownBy(() -> service.actualizar("b1", new BarrioRequest("Manga", "Histórica", List.of())))
                .isInstanceOf(InvalidRequestException.class);
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Barrio.class));
    }

    @Test
    void actualizarPuedeConservarSuPropioNombrePeroNoTomarElDeOtroBarrio() {
        when(barrioRepository.findAll()).thenReturn(List.of(Barrio.builder().id("b1").nombre("Manga").build(),
                Barrio.builder().id("b2").nombre("Pie de la Popa").build()));
        findAndModifyDevuelve(new Barrio());

        service.actualizar("b1", new BarrioRequest("manga", "Histórica", null));

        assertThatThrownBy(() -> service.actualizar("b1", new BarrioRequest("Pie de la popa", "Histórica", null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void actualizarUnBarrioInexistenteFalla() {
        assertThatThrownBy(() -> service.actualizar("x", new BarrioRequest("Manga", "Histórica", null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("x");
    }

    @Test
    void agregarCalleHacePushSinReescribirLasDemas() {
        findAndModifyDevuelve(new Barrio());

        service.agregarCalle("b1", new CalleRequest(" Segunda ", " 130003 "));

        Document cambios = cambiosEnviados();
        assertThat(cambios).containsOnlyKeys("$push");
        Calle nueva = (Calle) cambios.get("$push", Document.class).get("calles");
        assertThat(nueva.getNombre()).isEqualTo("Segunda");
        assertThat(nueva.getCodigoPostal()).isEqualTo("130003");
        assertThat(nueva.getId()).isNotBlank();
    }

    @Test
    void agregarCalleAUnBarrioInexistenteFalla() {
        assertThatThrownBy(() -> service.agregarCalle("x", new CalleRequest("Única", "130004")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("x");
    }

    @Test
    void eliminarCalleHacePullSoloDeLaCalleIndicada() {
        Barrio esperado = Barrio.builder().id("b1").calles(List.of(new Calle("c2", "Segunda", "130002"))).build();
        findAndModifyDevuelve(esperado);

        Barrio resultado = service.eliminarCalle("b1", "c1");

        assertThat(resultado).isSameAs(esperado);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(query.capture(), update.capture(), any(FindAndModifyOptions.class), eq(Barrio.class));
        assertThat(query.getValue().getQueryObject()).containsEntry("_id", "b1").containsEntry("calles.id", "c1");
        assertThat(update.getValue().getUpdateObject()).containsOnlyKeys("$pull");
    }

    @Test
    void eliminarCalleInexistenteFallaConLaCalleEnElMensaje() {
        barrioExistente(new Calle("c1", "Primera", "130001"));

        assertThatThrownBy(() -> service.eliminarCalle("b1", "no-existe"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Calle no encontrada")
                .hasMessageContaining("no-existe");
    }

    @Test
    void eliminarCalleDeUnBarrioInexistenteFallaConElBarrioEnElMensaje() {
        when(barrioRepository.findById("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminarCalle("x", "c1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Barrio no encontrado");
    }

    @Test
    void existeCalleDependeDeLoQueEncuentreElRepositorio() {
        when(barrioRepository.findByCallesId("c1")).thenReturn(Optional.of(new Barrio()));
        when(barrioRepository.findByCallesId("c2")).thenReturn(Optional.empty());

        assertThat(service.existeCalle("c1")).isTrue();
        assertThat(service.existeCalle("c2")).isFalse();
    }

    @Test
    void eliminarBorraElBarrio() {
        Barrio barrio = barrioExistente();

        service.eliminar("b1");

        verify(barrioRepository).delete(barrio);
    }

    @Test
    void eliminarUnBarrioInexistenteFallaYNoBorra() {
        when(barrioRepository.findById("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminar("x")).isInstanceOf(ResourceNotFoundException.class);
        verify(barrioRepository, never()).delete(any(Barrio.class));
    }
}


