package com.viactg.service;

import com.viactg.dto.UsuarioActualizacionRequest;
import com.viactg.dto.UsuarioRegistroRequest;
import com.viactg.exception.BusinessRuleException;
import com.viactg.exception.DuplicateResourceException;
import com.viactg.exception.ResourceNotFoundException;
import com.viactg.model.Rol;
import com.viactg.model.Usuario;
import com.viactg.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Cuentas de usuario. Las modificaciones de una cuenta existente se hacen con actualizaciones parciales
 * atómicas ({@code findAndModify} con {@code $set}/{@code $inc}) y nunca con {@code save()} del documento
 * completo: así un cambio de perfil del usuario no puede deshacer una desactivación o un cambio de rol que un
 * administrador hizo al mismo tiempo, ni al revés con la contraseña.
 */
@Service
public class UsuarioService {
    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);
    /** BCrypt solo procesa los primeros 72 bytes de la contraseña. */
    static final int MAX_BYTES_PASSWORD = 72;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final MongoTemplate mongoTemplate;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, MongoTemplate mongoTemplate) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.mongoTemplate = mongoTemplate;
    }

    /** Forma canónica del correo (independiente del idioma del servidor). */
    public static String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Las contraseñas se validan en caracteres, pero BCrypt cuenta bytes: con tildes o ñ una contraseña de 72
     * caracteres puede pasarse, y Spring Security rechaza esas contraseñas con un error interno.
     */
    public static void validarLongitudPassword(String password) {
        if (password != null && password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES_PASSWORD) {
            throw new BusinessRuleException("La contraseña es demasiado larga: usa como máximo 72 caracteres "
                    + "(las letras con tilde y la ñ cuentan doble)");
        }
    }

    public Usuario registrar(UsuarioRegistroRequest request) {
        String email = normalizarEmail(request.email());
        if (usuarioRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Ya existe un usuario con ese email");
        }
        validarLongitudPassword(request.password());
        Usuario usuario = Usuario.builder().nombre(request.nombre().trim()).email(email)
                .passwordHash(passwordEncoder.encode(request.password())).telefono(limpiarTelefono(request.telefono()))
                .rol(Rol.CIUDADANO).activo(true).fechaRegistro(Instant.now()).build();
        try {
            return usuarioRepository.save(usuario);
        } catch (DuplicateKeyException carrera) {
            // Dos registros simultáneos con el mismo correo: el índice único rechaza el segundo.
            throw new DuplicateResourceException("Ya existe un usuario con ese email");
        }
    }

    public Usuario buscarPorId(String id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + id));
    }

    public Usuario buscarPorEmail(String email) {
        return usuarioRepository.findByEmail(normalizarEmail(email))
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    public List<Usuario> listar() {
        return usuarioRepository.findAll();
    }

    public Usuario actualizarPerfil(String id, UsuarioActualizacionRequest request) {
        Update update = new Update();
        if (request.nombre() != null) update.set("nombre", request.nombre().trim());
        // Un teléfono vacío borra el que hubiera.
        if (request.telefono() != null) update.set("telefono", limpiarTelefono(request.telefono()));
        if (update.getUpdateObject().isEmpty()) return buscarPorId(id);
        return actualizarCampos(id, update);
    }

    public Usuario cambiarRol(String id, Rol rol, String solicitanteId) {
        if (id.equals(solicitanteId)) {
            throw new BusinessRuleException("No puedes cambiar tu propio rol");
        }
        return actualizarCampos(id, new Update().set("rol", rol));
    }

    public Usuario cambiarActivo(String id, boolean activo, String solicitanteId) {
        if (id.equals(solicitanteId)) {
            throw new BusinessRuleException("No puedes desactivar tu propia cuenta");
        }
        return actualizarCampos(id, new Update().set("activo", activo));
    }

    /** Guarda una contraseña nueva e invalida todos los JWT emitidos antes (sube la versión del token). */
    public Usuario actualizarPassword(String id, String passwordNueva) {
        validarLongitudPassword(passwordNueva);
        return actualizarCampos(id, new Update().set("passwordHash", passwordEncoder.encode(passwordNueva)).inc("versionToken", 1));
    }

    /** Invalida todos los JWT emitidos hasta ahora para este usuario. */
    public Usuario incrementarVersionToken(String id) {
        return actualizarCampos(id, new Update().inc("versionToken", 1));
    }

    /**
     * Crea o promueve el administrador inicial. Un administrador existente conserva su contraseña (pudo
     * cambiarla desde su perfil). Pero si la cuenta existía sin ser administradora, se le asigna ADMIN_PASSWORD
     * y se cierran sus sesiones: el registro no verifica la propiedad del correo, así que cualquiera pudo crear
     * antes una cuenta con ese email y, sin este paso, quedaría como administrador con su propia contraseña.
     */
    public Usuario asegurarAdministrador(String email, String password, String nombre) {
        String emailNormalizado = normalizarEmail(email);
        Optional<Usuario> existente = usuarioRepository.findByEmail(emailNormalizado);
        if (existente.isEmpty()) {
            validarLongitudPassword(password);
            return usuarioRepository.save(Usuario.builder().nombre(nombre).email(emailNormalizado)
                    .passwordHash(passwordEncoder.encode(password)).rol(Rol.ADMIN).activo(true)
                    .fechaRegistro(Instant.now()).build());
        }
        Usuario usuario = existente.get();
        if (usuario.getRol() == Rol.ADMIN) {
            return usuario.isActivo() ? usuario : actualizarCampos(usuario.getId(), new Update().set("activo", true));
        }
        validarLongitudPassword(password);
        log.warn("La cuenta {} existía sin rol de administrador: se promovió, su contraseña pasó a ser ADMIN_PASSWORD "
                + "y se cerraron sus sesiones", emailNormalizado);
        return actualizarCampos(usuario.getId(), new Update().set("rol", Rol.ADMIN).set("activo", true)
                .set("passwordHash", passwordEncoder.encode(password)).inc("versionToken", 1));
    }

    public String nombrePorId(String id) {
        return usuarioRepository.findById(id).map(Usuario::getNombre).orElse(null);
    }

    public Map<String, String> nombresPorId(Collection<String> ids) {
        Map<String, String> nombres = new HashMap<>();
        usuarioRepository.findAllById(ids).forEach(usuario -> nombres.put(usuario.getId(), usuario.getNombre()));
        return nombres;
    }

    public boolean esAdministradorOModerador(String id) {
        Rol rol = buscarPorId(id).getRol();
        return rol == Rol.ADMIN || rol == Rol.MODERADOR;
    }

    private Usuario actualizarCampos(String id, Update update) {
        Usuario actualizado = mongoTemplate.findAndModify(Query.query(Criteria.where("_id").is(id)), update,
                FindAndModifyOptions.options().returnNew(true), Usuario.class);
        if (actualizado == null) {
            throw new ResourceNotFoundException("Usuario no encontrado: " + id);
        }
        return actualizado;
    }

    private static String limpiarTelefono(String telefono) {
        if (telefono == null) return null;
        String limpio = telefono.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
