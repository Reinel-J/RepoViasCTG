package com.viactg.service;

import com.viactg.dto.CategoriaRequest;
import com.viactg.exception.DuplicateResourceException;
import com.viactg.model.Categoria;
import com.viactg.repository.CategoriaRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoriaServiceTests {
    private final CategoriaRepository categoriaRepository = mock(CategoriaRepository.class);
    private final AuditoriaService auditoriaService = mock(AuditoriaService.class);
    private final CategoriaService service = new CategoriaService(categoriaRepository, auditoriaService);

    private void catalogo(String... nombres) {
        when(categoriaRepository.findAll()).thenReturn(java.util.Arrays.stream(nombres)
                .map(nombre -> Categoria.builder().id("id-" + nombre).nombre(nombre).activa(true).build()).toList());
    }

    @Test
    void usaElNombreDelCatalogoSinImportarTildesMayusculasNiEspacios() {
        catalogo("Hueco", "Inundación", "Vía sin pavimentar");

        assertThat(service.normalizarNombre("  inundacion ")).isEqualTo("Inundación");
        assertThat(service.normalizarNombre("VIA   SIN pavimentar")).isEqualTo("Vía sin pavimentar");
    }

    @Test
    void unaCategoriaNuevaSeConservaComoLaEscribioLaPersona() {
        catalogo("Hueco");

        assertThat(service.normalizarNombre("  Semáforo   dañado ")).isEqualTo("Semáforo dañado");
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
    void cambiarEstadoConActorRegistraLaAccionDeAuditoria() {
        AuditoriaService auditoria = mock(AuditoriaService.class);
        CategoriaService servicioAuditado = new CategoriaService(categoriaRepository, auditoria);
        Categoria categoria = Categoria.builder().id("c1").nombre("Hueco").activa(true).build();
        when(categoriaRepository.findById("c1")).thenReturn(java.util.Optional.of(categoria));
        when(categoriaRepository.save(categoria)).thenReturn(categoria);

        servicioAuditado.cambiarEstado("c1", false, "admin");

        verify(auditoria).registrar("admin", "CATEGORIA_DESACTIVADA", "CATEGORIA", "c1", "Hueco");
    }
}
