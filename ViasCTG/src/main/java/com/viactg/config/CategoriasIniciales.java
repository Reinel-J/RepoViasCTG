package com.viactg.config;

import com.viactg.dto.CategoriaRequest;
import com.viactg.repository.CategoriaRepository;
import com.viactg.service.CategoriaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Crea las categorías iniciales del documento de visión (sección 6.4) cuando el catálogo está vacío, para que
 * el formulario de reportes tenga sugerencias desde el primer arranque. SEED_CATEGORIAS=false lo desactiva.
 */
@Component
public class CategoriasIniciales implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(CategoriasIniciales.class);

    static final List<CategoriaRequest> CATEGORIAS = List.of(
            new CategoriaRequest("Hueco", "Hueco o bache en la capa de rodadura"),
            new CategoriaRequest("Vía sin pavimentar", "Calle destapada o sin capa asfáltica"),
            new CategoriaRequest("Inundación", "Vía encharcada o inundada que dificulta el paso"),
            new CategoriaRequest("Hundimiento", "Hundimiento o deformación del pavimento"),
            new CategoriaRequest("Daño en alcantarillado", "Alcantarilla o sumidero destapado, roto o colapsado"),
            new CategoriaRequest("Derrumbe", "Material caído o deslizamiento de tierra sobre la vía"),
            new CategoriaRequest("Obstáculo en la vía", "Escombros, objetos o vehículos que bloquean el paso"));

    private final CategoriaRepository categoriaRepository;
    private final CategoriaService categoriaService;
    private final boolean habilitado;

    public CategoriasIniciales(CategoriaRepository categoriaRepository, CategoriaService categoriaService,
                               @Value("${app.seed.categorias:true}") boolean habilitado) {
        this.categoriaRepository = categoriaRepository;
        this.categoriaService = categoriaService;
        this.habilitado = habilitado;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!habilitado || categoriaRepository.count() > 0) return;
        CATEGORIAS.forEach(categoriaService::crear);
        log.info("Catálogo vacío: se crearon {} categorías iniciales", CATEGORIAS.size());
    }
}
