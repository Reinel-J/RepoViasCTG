package com.viactg.service;

import com.viactg.dto.BarrioRequest;
import com.viactg.dto.CalleRequest;
import com.viactg.exception.DuplicateResourceException;
import com.viactg.exception.InvalidRequestException;
import com.viactg.exception.ResourceNotFoundException;
import com.viactg.model.Barrio;
import com.viactg.model.Calle;
import com.viactg.repository.BarrioRepository;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class BarrioService {
    private final BarrioRepository barrioRepository;
    private final MongoTemplate mongoTemplate;

    public BarrioService(BarrioRepository barrioRepository, MongoTemplate mongoTemplate) {
        this.barrioRepository = barrioRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public Barrio crear(BarrioRequest request) {
        String nombre = request.nombre().trim();
        validarNombreLibre(nombre, null);
        return barrioRepository.save(Barrio.builder().nombre(nombre).nombreClave(CategoriaService.claveComparacion(nombre))
                .localidad(request.localidad().trim()).calles(mapearCalles(request.calles())).build());
    }

    public List<Barrio> listar() { return barrioRepository.findAll(); }

    public Barrio buscarPorId(String id) {
        return barrioRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Barrio no encontrado: " + id));
    }

    /**
     * Cambia nombre y localidad; las calles no se tocan. Se gestionan con agregarCalle/eliminarCalle: reemplazarlas aquí
     * borraba todas las calles cuando el cuerpo no las traía, y regeneraba los id de las que sí.
     */
    public Barrio actualizar(String id, BarrioRequest request) {
        if (request.calles() != null) {
            throw new InvalidRequestException("Las calles no se modifican al editar el barrio: agrégalas o quítalas con "
                    + "POST /api/barrios/{id}/calles y DELETE /api/barrios/{id}/calles/{calleId}");
        }
        String nombre = request.nombre().trim();
        validarNombreLibre(nombre, id);
        return modificar(Query.query(Criteria.where("_id").is(id)), new Update().set("nombre", nombre)
                .set("nombreClave", CategoriaService.claveComparacion(nombre)).set("localidad", request.localidad().trim()), id);
    }

    public Barrio agregarCalle(String barrioId, CalleRequest request) {
        Calle calle = new Calle(UUID.randomUUID().toString(), request.nombre().trim(), request.codigoPostal().trim());
        return modificar(Query.query(Criteria.where("_id").is(barrioId)), new Update().push("calles", calle), barrioId);
    }

    public Barrio eliminarCalle(String barrioId, String calleId) {
        Query conLaCalle = Query.query(Criteria.where("_id").is(barrioId).and("calles.id").is(calleId));
        Barrio actualizado = mongoTemplate.findAndModify(conLaCalle,
                new Update().pull("calles", Query.query(Criteria.where("id").is(calleId))),
                FindAndModifyOptions.options().returnNew(true), Barrio.class);
        if (actualizado != null) return actualizado;
        buscarPorId(barrioId); // 404 del barrio si no existe; si existe, la que falta es la calle.
        throw new ResourceNotFoundException("Calle no encontrada: " + calleId);
    }

    public boolean existeCalle(String calleId) {
        return barrioRepository.findByCallesId(calleId).isPresent();
    }

    public void eliminar(String id) {
        barrioRepository.delete(buscarPorId(id));
    }

    // Actualización parcial atómica: otra petición simultánea sobre el mismo barrio no pierde sus cambios.
    private Barrio modificar(Query query, Update update, String id) {
        Barrio actualizado = mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), Barrio.class);
        if (actualizado == null) throw new ResourceNotFoundException("Barrio no encontrado: " + id);
        return actualizado;
    }

    // Compara también con los barrios creados antes de nombreClave; el índice único cubre las carreras entre altas.
    private void validarNombreLibre(String nombre, String idActual) {
        String clave = CategoriaService.claveComparacion(nombre);
        barrioRepository.findAll().stream()
                .filter(barrio -> barrio.getNombre() != null && CategoriaService.claveComparacion(barrio.getNombre()).equals(clave))
                .filter(barrio -> !Objects.equals(barrio.getId(), idActual))
                .findFirst()
                .ifPresent(existente -> {
                    throw new DuplicateResourceException("Ya existe el barrio «" + existente.getNombre() + "»");
                });
    }

    private List<Calle> mapearCalles(List<CalleRequest> calles) {
        if (calles == null) return new ArrayList<>();
        return calles.stream().map(c -> new Calle(UUID.randomUUID().toString(), c.nombre().trim(), c.codigoPostal().trim())).toList();
    }
}
