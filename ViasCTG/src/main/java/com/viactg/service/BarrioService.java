package com.viactg.service;

import com.viactg.dto.BarrioRequest;
import com.viactg.dto.CalleRequest;
import com.viactg.exception.ResourceNotFoundException;
import com.viactg.model.Barrio;
import com.viactg.model.Calle;
import com.viactg.repository.BarrioRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class BarrioService {
    private final BarrioRepository barrioRepository;

    public BarrioService(BarrioRepository barrioRepository) {
        this.barrioRepository = barrioRepository;
    }

    public Barrio crear(BarrioRequest request) {
        return barrioRepository.save(Barrio.builder().nombre(request.nombre().trim()).localidad(request.localidad().trim())
                .calles(mapearCalles(request.calles())).build());
    }

    public List<Barrio> listar() { return barrioRepository.findAll(); }

    public Barrio buscarPorId(String id) {
        return barrioRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Barrio no encontrado: " + id));
    }

    public Barrio actualizar(String id, BarrioRequest request) {
        Barrio barrio = buscarPorId(id);
        barrio.setNombre(request.nombre().trim());
        barrio.setLocalidad(request.localidad().trim());
        barrio.setCalles(mapearCalles(request.calles()));
        return barrioRepository.save(barrio);
    }

    public Barrio agregarCalle(String barrioId, CalleRequest request) {
        Barrio barrio = buscarPorId(barrioId);
        callesMutables(barrio).add(new Calle(UUID.randomUUID().toString(), request.nombre().trim(), request.codigoPostal().trim()));
        return barrioRepository.save(barrio);
    }

    public Barrio eliminarCalle(String barrioId, String calleId) {
        Barrio barrio = buscarPorId(barrioId);
        boolean eliminada = callesMutables(barrio).removeIf(calle -> calle.getId().equals(calleId));
        if (!eliminada) throw new ResourceNotFoundException("Calle no encontrada: " + calleId);
        return barrioRepository.save(barrio);
    }

    public boolean existeCalle(String calleId) {
        return barrioRepository.findByCallesId(calleId).isPresent();
    }

    public void eliminar(String id) {
        barrioRepository.delete(buscarPorId(id));
    }

    // Las listas creadas con toList() son inmutables; se copia antes de modificar.
    private List<Calle> callesMutables(Barrio barrio) {
        barrio.setCalles(barrio.getCalles() == null ? new ArrayList<>() : new ArrayList<>(barrio.getCalles()));
        return barrio.getCalles();
    }

    private List<Calle> mapearCalles(List<CalleRequest> calles) {
        if (calles == null) return new java.util.ArrayList<>();
        return calles.stream().map(c -> new Calle(UUID.randomUUID().toString(), c.nombre().trim(), c.codigoPostal().trim())).toList();
    }
}
