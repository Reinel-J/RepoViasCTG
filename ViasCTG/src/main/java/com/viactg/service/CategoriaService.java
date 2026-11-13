package com.viactg.service;

import com.viactg.dto.CategoriaRequest;
import com.viactg.exception.DuplicateResourceException;
import com.viactg.exception.ResourceNotFoundException;
import com.viactg.model.Categoria;
import com.viactg.repository.CategoriaRepository;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Service
public class CategoriaService {
    private final CategoriaRepository categoriaRepository;
    private final AuditoriaService auditoriaService;

    public CategoriaService(CategoriaRepository categoriaRepository, AuditoriaService auditoriaService) {
        this.categoriaRepository = categoriaRepository;
        this.auditoriaService = auditoriaService;
    }

    public Categoria crear(CategoriaRequest request) {
        String nombre = limpiar(request.nombre());
        validarNombreLibre(nombre, null);
        return categoriaRepository.save(Categoria.builder().nombre(nombre).descripcion(request.descripcion()).activa(true).build());
    }
    public Categoria crear(CategoriaRequest request, String actorId) { Categoria creada = crear(request); auditar(actorId, "CATEGORIA_CREADA", creada.getId(), creada.getNombre()); return creada; }

    public List<Categoria> listar(boolean soloActivas) {
        return soloActivas ? categoriaRepository.findByActivaTrue() : categoriaRepository.findAll();
    }

    public Categoria buscarPorId(String id) {
        return categoriaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada: " + id));
    }

    public Categoria actualizar(String id, CategoriaRequest request) {
        Categoria categoria = buscarPorId(id);
        String nombre = limpiar(request.nombre());
        validarNombreLibre(nombre, id);
        categoria.setNombre(nombre);
        categoria.setDescripcion(request.descripcion());
        return categoriaRepository.save(categoria);
    }
    public Categoria actualizar(String id, CategoriaRequest request, String actorId) { Categoria actualizada = actualizar(id, request); auditar(actorId, "CATEGORIA_EDITADA", id, actualizada.getNombre()); return actualizada; }

    public Categoria cambiarEstado(String id, boolean activa) {
        Categoria categoria = buscarPorId(id);
        categoria.setActiva(activa);
        return categoriaRepository.save(categoria);
    }
    public Categoria cambiarEstado(String id, boolean activa, String actorId) { Categoria actualizada = cambiarEstado(id, activa); auditar(actorId, activa ? "CATEGORIA_ACTIVADA" : "CATEGORIA_DESACTIVADA", id, actualizada.getNombre()); return actualizada; }
    private void auditar(String actorId, String accion, String id, String detalle) {
        auditoriaService.registrar(actorId, accion, "CATEGORIA", id, detalle);
    }

    /**
     * La categoría de un reporte es texto libre, pero si coincide con una del catálogo (sin distinguir
     * mayúsculas, tildes ni espacios repetidos) se guarda con el nombre oficial. Así "inundacion " y
     * "Inundación" quedan juntas en filtros y estadísticas.
     */
    public String normalizarNombre(String texto) {
        String limpio = limpiar(texto);
        if (limpio.isEmpty()) return limpio;
        return buscarPorNombre(limpio).map(Categoria::getNombre).orElse(limpio);
    }

    /** Forma de comparación: sin tildes, en minúsculas y con los espacios normalizados. */
    static String claveComparacion(String texto) {
        String sinTildes = Normalizer.normalize(limpiar(texto), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT);
    }

    private Optional<Categoria> buscarPorNombre(String nombre) {
        String clave = claveComparacion(nombre);
        return categoriaRepository.findAll().stream()
                .filter(categoria -> categoria.getNombre() != null && claveComparacion(categoria.getNombre()).equals(clave))
                .findFirst();
    }

    private void validarNombreLibre(String nombre, String idActual) {
        buscarPorNombre(nombre).filter(existente -> !Objects.equals(existente.getId(), idActual)).ifPresent(existente -> {
            throw new DuplicateResourceException("Ya existe la categoría «" + existente.getNombre() + "»");
        });
    }

    private static String limpiar(String texto) {
        return texto == null ? "" : texto.trim().replaceAll("\\s+", " ");
    }
}
