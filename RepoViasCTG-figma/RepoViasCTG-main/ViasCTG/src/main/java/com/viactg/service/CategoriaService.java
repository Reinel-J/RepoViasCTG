package com.viactg.service;

import com.viactg.dto.CategoriaRequest;
import com.viactg.exception.ResourceNotFoundException;
import com.viactg.model.Categoria;
import com.viactg.repository.CategoriaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoriaService {
    private final CategoriaRepository categoriaRepository;

    public CategoriaService(CategoriaRepository categoriaRepository) { this.categoriaRepository = categoriaRepository; }

    public Categoria crear(CategoriaRequest request) {
        return categoriaRepository.save(Categoria.builder().nombre(request.nombre().trim()).descripcion(request.descripcion()).activa(true).build());
    }

    public List<Categoria> listar(boolean soloActivas) {
        return soloActivas ? categoriaRepository.findByActivaTrue() : categoriaRepository.findAll();
    }

    public Categoria buscarPorId(String id) {
        return categoriaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada: " + id));
    }

    public Categoria actualizar(String id, CategoriaRequest request) {
        Categoria categoria = buscarPorId(id);
        categoria.setNombre(request.nombre().trim());
        categoria.setDescripcion(request.descripcion());
        return categoriaRepository.save(categoria);
    }

    public Categoria cambiarEstado(String id, boolean activa) {
        Categoria categoria = buscarPorId(id);
        categoria.setActiva(activa);
        return categoriaRepository.save(categoria);
    }
}
