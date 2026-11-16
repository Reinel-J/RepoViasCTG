package com.viactg.service;

import com.viactg.dto.CategoriaRequest;
import com.viactg.exception.BusinessRuleException;
import com.viactg.exception.DuplicateResourceException;
import com.viactg.exception.ResourceNotFoundException;
import com.viactg.model.Categoria;
import com.viactg.model.CategoriaReporte;
import com.viactg.model.Reporte;
import com.viactg.repository.CategoriaRepository;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Catálogo de categorías. Un reporte guarda su categoría embebida ({@link CategoriaReporte}: id y copia del nombre), así
 * que renombrar una categoría también actualiza esa copia en sus reportes. Las categorías no se borran: se desactivan.
 */
@Service
public class CategoriaService {
    /** Categoría para los daños que no encajan en ninguna otra; también recibe los textos antiguos sin equivalente. */
    public static final String NOMBRE_OTRO = "Otro";
    static final String DESCRIPCION_OTRO = "Daño que no encaja en ninguna de las demás categorías";

    private final CategoriaRepository categoriaRepository;
    private final MongoTemplate mongoTemplate;
    private final AuditoriaService auditoriaService;
    private final ApplicationEventPublisher eventos;

    public CategoriaService(CategoriaRepository categoriaRepository, MongoTemplate mongoTemplate, AuditoriaService auditoriaService,
                            ApplicationEventPublisher eventos) {
        this.categoriaRepository = categoriaRepository;
        this.mongoTemplate = mongoTemplate;
        this.auditoriaService = auditoriaService;
        this.eventos = eventos;
    }

    public Categoria crear(CategoriaRequest request) {
        String nombre = limpiar(request.nombre());
        validarNombreLibre(nombre, null);
        return avisar(categoriaRepository.save(Categoria.builder().nombre(nombre).descripcion(request.descripcion()).activa(true).build()));
    }
    public Categoria crear(CategoriaRequest request, String actorId) { Categoria creada = crear(request); auditar(actorId, "CATEGORIA_CREADA", creada.getId(), creada.getNombre()); return creada; }

    public List<Categoria> listar(boolean soloActivas) {
        return soloActivas ? categoriaRepository.findByActivaTrue() : categoriaRepository.findAll();
    }

    public Categoria buscarPorId(String id) {
        return categoriaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada: " + id));
    }

    /** Cambia nombre y descripción. Si cambia el nombre, actualiza la copia embebida en los reportes de esa categoría. */
    public Categoria actualizar(String id, CategoriaRequest request) {
        return actualizarConConteo(id, request).categoria();
    }
    public Categoria actualizar(String id, CategoriaRequest request, String actorId) {
        Renombre resultado = actualizarConConteo(id, request);
        String detalle = resultado.anterior().equals(resultado.categoria().getNombre())
                ? resultado.categoria().getNombre()
                : resultado.anterior() + " → " + resultado.categoria().getNombre() + " (" + resultado.reportes() + " reporte(s) actualizados)";
        auditar(actorId, "CATEGORIA_EDITADA", id, detalle);
        return resultado.categoria();
    }

    private Renombre actualizarConConteo(String id, CategoriaRequest request) {
        String nombre = limpiar(request.nombre());
        validarNombreLibre(nombre, id);
        // Actualización parcial atómica; devuelve el documento anterior para saber si cambió el nombre.
        Categoria anterior = mongoTemplate.findAndModify(Query.query(Criteria.where("_id").is(id)),
                new Update().set("nombre", nombre).set("descripcion", request.descripcion()),
                FindAndModifyOptions.options().returnNew(false), Categoria.class);
        if (anterior == null) throw new ResourceNotFoundException("Categoría no encontrada: " + id);
        long reportes = nombre.equals(anterior.getNombre()) ? 0 : mongoTemplate.updateMulti(
                Query.query(Criteria.where("categoria.id").is(id)), new Update().set("categoria.nombre", nombre), Reporte.class)
                .getModifiedCount();
        Categoria actualizada = Categoria.builder().id(id).nombre(nombre).descripcion(request.descripcion())
                .activa(anterior.isActiva()).build();
        avisar(actualizada);
        return new Renombre(actualizada, anterior.getNombre(), reportes);
    }

    private record Renombre(Categoria categoria, String anterior, long reportes) {
    }

    public Categoria cambiarEstado(String id, boolean activa) {
        Categoria actualizada = mongoTemplate.findAndModify(Query.query(Criteria.where("_id").is(id)),
                new Update().set("activa", activa), FindAndModifyOptions.options().returnNew(true), Categoria.class);
        if (actualizada == null) throw new ResourceNotFoundException("Categoría no encontrada: " + id);
        return avisar(actualizada);
    }

    // En la réplica SQL el nombre vive solo en la tabla categorias: un renombre no toca las filas de reportes.
    private Categoria avisar(Categoria categoria) {
        if (categoria != null) eventos.publishEvent(CambioReplicable.categoria(categoria.getId()));
        return categoria;
    }
    public Categoria cambiarEstado(String id, boolean activa, String actorId) { Categoria actualizada = cambiarEstado(id, activa); auditar(actorId, activa ? "CATEGORIA_ACTIVADA" : "CATEGORIA_DESACTIVADA", id, actualizada.getNombre()); return actualizada; }
    private void auditar(String actorId, String accion, String id, String detalle) {
        auditoriaService.registrar(actorId, accion, "CATEGORIA", id, detalle);
    }

    /**
     * La categoría que se embebe en un reporte nuevo: debe existir en el catálogo y estar activa. Una desactivada sigue
     * en los reportes que ya la tenían, pero no se puede elegir para reportes nuevos.
     */
    public CategoriaReporte paraReporte(String categoriaId) {
        Categoria categoria = categoriaRepository.findById(categoriaId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("La categoría elegida no existe; recarga la página y elige otra"));
        if (!categoria.isActiva()) {
            throw new BusinessRuleException("La categoría «" + categoria.getNombre() + "» ya no está disponible; elige otra");
        }
        return CategoriaReporte.de(categoria);
    }

    /** La categoría «Otro»; si no existe, la crea activa. */
    public Categoria asegurarOtro() {
        return buscarPorNombre(NOMBRE_OTRO).orElseGet(() -> crear(new CategoriaRequest(NOMBRE_OTRO, DESCRIPCION_OTRO)));
    }

    /** Forma de comparación: sin tildes, en minúsculas y con los espacios normalizados. */
    static String claveComparacion(String texto) {
        String sinTildes = Normalizer.normalize(limpiar(texto), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT);
    }

    /** Categoría del catálogo con ese nombre, sin distinguir mayúsculas, tildes ni espacios repetidos. */
    public Optional<Categoria> buscarPorNombre(String nombre) {
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
