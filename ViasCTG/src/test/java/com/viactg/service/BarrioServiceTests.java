package com.viactg.service;

import com.viactg.dto.BarrioRequest;
import com.viactg.dto.CalleRequest;
import com.viactg.exception.ResourceNotFoundException;
import com.viactg.model.Barrio;
import com.viactg.model.Calle;
import com.viactg.repository.BarrioRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BarrioServiceTests {
    private final BarrioRepository barrioRepository = mock(BarrioRepository.class);
    private final BarrioService service = new BarrioService(barrioRepository);

    private Barrio barrioExistente(Calle... calles) {
        Barrio barrio = Barrio.builder().id("b1").nombre("Getsemaní").localidad("Histórica")
                .calles(new ArrayList<>(List.of(calles))).build();
        when(barrioRepository.findById("b1")).thenReturn(Optional.of(barrio));
        when(barrioRepository.save(any(Barrio.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        return barrio;
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
    void actualizarReemplazaNombreLocalidadYCalles() {
        Barrio barrio = barrioExistente(new Calle("c-vieja", "Vieja", "130000"));

        Barrio actualizado = service.actualizar("b1", new BarrioRequest(" Manga ", " Industrial ",
                List.of(new CalleRequest("Calle Nueva", "130002"))));

        assertThat(actualizado).isSameAs(barrio);
        assertThat(actualizado.getNombre()).isEqualTo("Manga");
        assertThat(actualizado.getLocalidad()).isEqualTo("Industrial");
        assertThat(actualizado.getCalles()).extracting(Calle::getNombre).containsExactly("Calle Nueva");
        verify(barrioRepository).save(barrio);
    }

    @Test
    void agregarCalleFuncionaAunqueLaListaOriginalSeaInmutable() {
        Barrio barrio = barrioExistente();
        barrio.setCalles(List.of(new Calle("c1", "Primera", "130001"))); // toList() produce listas inmutables

        Barrio resultado = service.agregarCalle("b1", new CalleRequest(" Segunda ", " 130003 "));

        assertThat(resultado.getCalles()).hasSize(2);
        Calle nueva = resultado.getCalles().get(1);
        assertThat(nueva.getNombre()).isEqualTo("Segunda");
        assertThat(nueva.getCodigoPostal()).isEqualTo("130003");
        assertThat(nueva.getId()).isNotBlank();
    }

    @Test
    void agregarCalleFuncionaSiElBarrioNoTeniaListaDeCalles() {
        Barrio barrio = barrioExistente();
        barrio.setCalles(null);

        Barrio resultado = service.agregarCalle("b1", new CalleRequest("Única", "130004"));

        assertThat(resultado.getCalles()).extracting(Calle::getNombre).containsExactly("Única");
    }

    @Test
    void eliminarCalleQuitaSoloLaCalleIndicada() {
        barrioExistente(new Calle("c1", "Primera", "130001"), new Calle("c2", "Segunda", "130002"));

        Barrio resultado = service.eliminarCalle("b1", "c1");

        assertThat(resultado.getCalles()).extracting(Calle::getId).containsExactly("c2");
    }

    @Test
    void eliminarCalleInexistenteFallaYNoGuarda() {
        barrioExistente(new Calle("c1", "Primera", "130001"));

        assertThatThrownBy(() -> service.eliminarCalle("b1", "no-existe"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("no-existe");
        verify(barrioRepository, never()).save(any(Barrio.class));
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


