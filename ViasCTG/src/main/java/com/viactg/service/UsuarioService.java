package com.viactg.service;

import com.viactg.dto.UsuarioActualizacionRequest;
import com.viactg.dto.UsuarioRegistroRequest;
import com.viactg.exception.BusinessRuleException;
import com.viactg.exception.DuplicateResourceException;
import com.viactg.exception.ForbiddenOperationException;
import com.viactg.exception.InvalidRequestException;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
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
    private static final int MAX_NOMBRE = 100;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final MongoTemplate mongoTemplate;
    private final AuditoriaService auditoriaService;
    private final ApplicationEventPublisher eventos;
    private final SecureRandom random = new SecureRandom();

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, MongoTemplate mongoTemplate, AuditoriaService auditoriaService,
                          ApplicationEventPublisher eventos) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.mongoTemplate = mongoTemplate;
        this.auditoriaService = auditoriaService;
        this.eventos = eventos;
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
            throw new InvalidRequestException("La contraseña es demasiado larga: usa como máximo 72 caracteres "
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
                .rol(Rol.CIUDADANO).activo(true).emailVerificado(false).fechaRegistro(Instant.now()).build();
        try {
            return avisar(usuarioRepository.save(usuario));
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
        Rol anterior = buscarPorId(id).getRol();
        Usuario actualizado = actualizarCampos(id, new Update().set("rol", rol));
        auditar(solicitanteId, "USUARIO_ROL_CAMBIADO", id, anterior + " → " + actualizado.getRol());
        return actualizado;
    }

    public Usuario cambiarActivo(String id, boolean activo, String solicitanteId) {
        if (id.equals(solicitanteId)) {
            throw new BusinessRuleException("No puedes desactivar tu propia cuenta");
        }
        Usuario actualizado = actualizarCampos(id, new Update().set("activo", activo));
        auditar(solicitanteId, "USUARIO_ESTADO_CAMBIADO", id, activo ? "Cuenta activada" : "Cuenta desactivada");
        return actualizado;
    }

    /** Guarda una contraseña nueva e invalida todos los JWT emitidos antes (sube la versión del token). */
    /** Contraseña nueva desde el enlace de recuperación: abrirlo también prueba que el correo es de quien lo usa. */
    public Usuario restablecerPassword(String id, String passwordNueva) {
        validarLongitudPassword(passwordNueva);
        return actualizarCampos(id, new Update().set("passwordHash", passwordEncoder.encode(passwordNueva))
                .set("emailVerificado", true).inc("versionToken", 1));
    }

    public Usuario marcarEmailVerificado(String id) {
        return actualizarCampos(id, new Update().set("emailVerificado", true));
    }

    /**
     * Cuenta para quien entra con Google (correo ya verificado por Google y normalizado). Busca primero la cuenta
     * vinculada a ese {@code googleId}; si no hay, la del mismo correo, y si tampoco, crea una de ciudadano.
     * <ul>
     *   <li>Cuenta con el correo confirmado: se vincula y conserva su contraseña, su rol y sus datos.</li>
     *   <li>Cuenta sin confirmar: pudo registrarla otra persona con este correo (igual que al restablecer la
     *   contraseña). Se confirma, se descarta su contraseña y se cierran sus sesiones.</li>
     *   <li>Cuenta nueva: sin contraseña utilizable; si la quiere, la crea con «¿Olvidaste tu contraseña?».</li>
     * </ul>
     */
    public Usuario accederConGoogle(String googleId, String email, String nombreGoogle) {
        try {
            return vincularOCrearConGoogle(googleId, email, nombreGoogle);
        } catch (DuplicateKeyException carrera) {
            // Dos primeros accesos simultáneos: el índice único rechaza la segunda cuenta, que ahora ya existe.
            return vincularOCrearConGoogle(googleId, email, nombreGoogle);
        }
    }

    private Usuario vincularOCrearConGoogle(String googleId, String email, String nombreGoogle) {
        Optional<Usuario> vinculada = usuarioRepository.findByGoogleId(googleId);
        if (vinculada.isPresent()) {
            return activa(vinculada.get());
        }
        Optional<Usuario> mismoCorreo = usuarioRepository.findByEmail(email);
        if (mismoCorreo.isEmpty()) {
            return avisar(usuarioRepository.save(Usuario.builder().nombre(nombreDesdeGoogle(nombreGoogle, email)).email(email)
                    .googleId(googleId).passwordHash(passwordInutilizable()).rol(Rol.CIUDADANO).activo(true)
                    .emailVerificado(true).fechaRegistro(Instant.now()).build()));
        }
        Usuario existente = activa(mismoCorreo.get());
        if (existente.getGoogleId() != null) {
            // Otra cuenta de Google con el mismo correo (por ejemplo, un correo de empresa que se reasignó).
            throw new ForbiddenOperationException("Este correo ya está vinculado a otra cuenta de Google. Inicia sesión "
                    + "con tu contraseña o con la cuenta de Google de siempre.");
        }
        if (existente.isEmailVerificado()) {
            log.info("La cuenta {} se vinculó con Google", email);
            return actualizarCampos(existente.getId(), new Update().set("googleId", googleId));
        }
        log.warn("La cuenta {} estaba sin confirmar y se recuperó con Google: se descartó su contraseña y se cerraron sus "
                + "sesiones", email);
        return actualizarCampos(existente.getId(), new Update().set("googleId", googleId).set("emailVerificado", true)
                .set("nombre", nombreDesdeGoogle(nombreGoogle, email)).set("passwordHash", passwordInutilizable())
                .inc("versionToken", 1));
    }

    private static Usuario activa(Usuario usuario) {
        if (!usuario.isActivo()) {
            // Quien entra con Google ya demostró que el correo es suyo: se le puede decir por qué no entra.
            throw new ForbiddenOperationException("Esta cuenta está desactivada. Si crees que es un error, escribe al "
                    + "equipo de ViaCTG.");
        }
        return usuario;
    }

    /** El nombre que muestra Google (el registro admite hasta 100 caracteres); sin él, lo que va antes de la @. */
    static String nombreDesdeGoogle(String nombreGoogle, String email) {
        String nombre = nombreGoogle == null || nombreGoogle.isBlank() ? email.substring(0, email.indexOf('@')) : nombreGoogle.strip();
        return nombre.length() > MAX_NOMBRE ? nombre.substring(0, MAX_NOMBRE) : nombre;
    }

    // Hash de un secreto aleatorio que nadie conoce: con contraseña vacía o null, BCrypt fallaría o el login
    // tardaría menos y revelaría qué cuentas entran solo con Google.
    private String passwordInutilizable() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return passwordEncoder.encode(Base64.getEncoder().encodeToString(bytes));
    }

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
        // ADMIN_EMAIL lo fija quien administra el servidor: la cuenta queda con el correo verificado.
        if (existente.isEmpty()) {
            validarLongitudPassword(password);
            return avisar(usuarioRepository.save(Usuario.builder().nombre(nombre).email(emailNormalizado)
                    .passwordHash(passwordEncoder.encode(password)).rol(Rol.ADMIN).activo(true).emailVerificado(true)
                    .fechaRegistro(Instant.now()).build()));
        }
        Usuario usuario = existente.get();
        if (usuario.getRol() == Rol.ADMIN) {
            return usuario.isActivo() && usuario.isEmailVerificado() ? usuario
                    : actualizarCampos(usuario.getId(), new Update().set("activo", true).set("emailVerificado", true));
        }
        validarLongitudPassword(password);
        log.warn("La cuenta {} existía sin rol de administrador: se promovió, su contraseña pasó a ser ADMIN_PASSWORD "
                + "y se cerraron sus sesiones", emailNormalizado);
        return actualizarCampos(usuario.getId(), new Update().set("rol", Rol.ADMIN).set("activo", true).set("emailVerificado", true)
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
        return avisar(actualizado);
    }

    // Copia el usuario a la réplica SQL (sin la contraseña).
    private Usuario avisar(Usuario usuario) {
        if (usuario != null) eventos.publishEvent(CambioReplicable.usuario(usuario.getId()));
        return usuario;
    }
    private void auditar(String actorId, String accion, String entidadId, String detalle) {
        auditoriaService.registrar(actorId, accion, "USUARIO", entidadId, detalle);
    }

    private static String limpiarTelefono(String telefono) {
        if (telefono == null) return null;
        String limpio = telefono.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
