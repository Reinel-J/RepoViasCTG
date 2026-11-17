package com.viactg.service;

import com.viactg.dto.UsuarioRegistroRequest;
import com.viactg.exception.BusinessRuleException;
import com.viactg.exception.ForbiddenOperationException;
import com.viactg.model.TokenRecuperacion;
import com.viactg.model.TokenVerificacion;
import com.viactg.model.Usuario;
import com.viactg.repository.TokenRecuperacionRepository;
import com.viactg.repository.TokenVerificacionRepository;
import com.viactg.repository.UsuarioRepository;
import com.viactg.security.LimitadorIntentos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/** Autenticación y ciclo de vida de credenciales: login, registro y verificación del correo, contraseña y revocación de sesiones. */
@Service
public class AuthService {
    private static final Duration VENTANA_LOGIN = Duration.ofMinutes(15);
    private static final int MAX_LOGIN_POR_IP = 20;
    private static final int MAX_FALLOS_POR_EMAIL_E_IP = 5;
    // Techo por cuenta para frenar a quien reparte los intentos entre muchas IP. Es alto a propósito: si fuera
    // igual al de correo + IP, cualquiera podría dejar a la dueña fuera de su cuenta con unas pocas peticiones.
    private static final int MAX_INTENTOS_POR_EMAIL = 50;
    private static final Duration VENTANA_HORA = Duration.ofHours(1);
    private static final int MAX_REGISTROS_POR_IP = 5;
    private static final int MAX_RECUPERACIONES_POR_IP = 5;
    private static final int MAX_RECUPERACIONES_POR_EMAIL = 3;
    private static final int MAX_FALLOS_CAMBIO_PASSWORD = 5;
    private static final int MAX_REENVIOS_POR_IP = 5;
    private static final int MAX_REENVIOS_POR_EMAIL = 3;
    private static final String CREDENCIALES_INVALIDAS = "Credenciales inválidas o usuario inactivo";

    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final TokenRecuperacionRepository tokenRepository;
    private final TokenVerificacionRepository tokenVerificacionRepository;
    private final PasswordEncoder passwordEncoder;
    private final LimitadorIntentos limitador;
    private final CorreoService correoService;
    private final String frontendUrl;
    private final Duration vigenciaRecuperacion;
    private final Duration vigenciaVerificacion;
    private final SecureRandom random = new SecureRandom();
    // Hash ficticio para que un correo inexistente cueste lo mismo que una contraseña incorrecta.
    private final String hashFicticio;

    public AuthService(UsuarioRepository usuarioRepository, UsuarioService usuarioService, TokenRecuperacionRepository tokenRepository,
                       TokenVerificacionRepository tokenVerificacionRepository, PasswordEncoder passwordEncoder,
                       LimitadorIntentos limitador, CorreoService correoService, @Value("${app.frontend.url}") String frontendUrl,
                       @Value("${app.password-reset.expiration-minutes:30}") long minutosRecuperacion,
                       @Value("${app.email-verification.expiration-hours:48}") long horasVerificacion) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
        this.tokenRepository = tokenRepository;
        this.tokenVerificacionRepository = tokenVerificacionRepository;
        this.passwordEncoder = passwordEncoder;
        this.limitador = limitador;
        this.correoService = correoService;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.vigenciaRecuperacion = Duration.ofMinutes(minutosRecuperacion);
        this.vigenciaVerificacion = Duration.ofHours(horasVerificacion);
        this.hashFicticio = passwordEncoder.encode("contraseña-ficticia-para-igualar-tiempos");
    }

    public Usuario login(String email, String password, String ip) {
        String emailNormalizado = UsuarioService.normalizarEmail(email);
        String claveEmailIp = claveLoginEmailIp(emailNormalizado) + ip;
        limitador.consumir("login-ip:" + ip, MAX_LOGIN_POR_IP, VENTANA_LOGIN);
        // Los intentos se cuentan antes de comparar la contraseña (y el de correo + IP se borra si es correcta):
        // contar solo después de fallar dejaría probar muchas contraseñas a la vez con peticiones en paralelo.
        // Los fallos se cuentan por correo + IP, así que desde otra red no se puede bloquear a la dueña.
        limitador.consumir(claveEmailIp, MAX_FALLOS_POR_EMAIL_E_IP, VENTANA_LOGIN);
        limitador.consumir(claveLoginEmail(emailNormalizado), MAX_INTENTOS_POR_EMAIL, VENTANA_LOGIN);

        Optional<Usuario> usuario = usuarioRepository.findByEmail(emailNormalizado);
        boolean coincide = passwordEncoder.matches(password, usuario.map(Usuario::getPasswordHash).orElse(hashFicticio));
        if (usuario.isEmpty() || !coincide || !usuario.get().isActivo()) {
            throw new BadCredentialsException(CREDENCIALES_INVALIDAS);
        }
        limitador.limpiar(claveEmailIp);
        // Solo después de comprobar la contraseña: a quien no la sabe no se le dice si la cuenta está sin confirmar.
        if (!usuario.get().isEmailVerificado()) {
            throw new ForbiddenOperationException("Confirma tu correo antes de iniciar sesión: abre el enlace que te enviamos a "
                    + emailNormalizado + ". Si no te llegó, pide uno nuevo.");
        }
        return usuario.get();
    }

    /** Crea la cuenta sin verificar y envía el enlace de confirmación; no inicia sesión. */
    public Usuario registrar(UsuarioRegistroRequest request, String ip) {
        limitador.consumir("registro-ip:" + ip, MAX_REGISTROS_POR_IP, VENTANA_HORA);
        Usuario usuario = usuarioService.registrar(request);
        enviarVerificacion(usuario);
        return usuario;
    }

    /** Siempre termina igual exista o no la cuenta, para no revelar cuáles hay ni cuáles faltan por confirmar. */
    public void reenviarVerificacion(String email, String ip) {
        limitador.consumir("verificacion-ip:" + ip, MAX_REENVIOS_POR_IP, VENTANA_HORA);
        String emailNormalizado = UsuarioService.normalizarEmail(email);
        try {
            limitador.consumir("verificacion-email:" + emailNormalizado, MAX_REENVIOS_POR_EMAIL, VENTANA_HORA);
        } catch (RuntimeException limiteAlcanzado) {
            return;
        }
        usuarioRepository.findByEmail(emailNormalizado).filter(usuario -> usuario.isActivo() && !usuario.isEmailVerificado())
                .ifPresent(this::enviarVerificacion);
    }

    /** Marca el correo como verificado; devuelve el usuario para iniciar sesión de una vez. El enlace es de un solo uso. */
    public Usuario verificarEmail(String token) {
        TokenVerificacion registro = tokenVerificacionRepository.findByTokenHash(sha256(token))
                .filter(encontrado -> encontrado.getExpira().isAfter(Instant.now()))
                .orElseThrow(() -> new BusinessRuleException("El enlace no es válido, ya se usó o venció. Si ya confirmaste tu "
                        + "correo, inicia sesión; si no, pide un enlace nuevo."));
        Usuario usuario = usuarioService.buscarPorId(registro.getUsuarioId());
        if (!usuario.isActivo()) {
            throw new BusinessRuleException("Esta cuenta está desactivada");
        }
        tokenVerificacionRepository.deleteByUsuarioId(usuario.getId());
        return usuarioService.marcarEmailVerificado(usuario.getId());
    }

    /** Siempre termina igual exista o no el correo, para no revelar qué cuentas hay. */
    public void solicitarRecuperacion(String email, String ip) {
        limitador.consumir("recuperar-ip:" + ip, MAX_RECUPERACIONES_POR_IP, VENTANA_HORA);
        String emailNormalizado = UsuarioService.normalizarEmail(email);
        String claveEmail = "recuperar-email:" + emailNormalizado;
        try {
            limitador.consumir(claveEmail, MAX_RECUPERACIONES_POR_EMAIL, VENTANA_HORA);
        } catch (RuntimeException limiteAlcanzado) {
            return;
        }
        usuarioRepository.findByEmail(emailNormalizado).filter(Usuario::isActivo).ifPresent(usuario -> {
            tokenRepository.deleteByUsuarioId(usuario.getId());
            String token = generarToken();
            tokenRepository.save(TokenRecuperacion.builder().usuarioId(usuario.getId()).tokenHash(sha256(token))
                    .expira(Instant.now().plus(vigenciaRecuperacion)).build());
            String enlace = frontendUrl + "/restablecer?token=" + token;
            correoService.enviar(usuario.getEmail(), "Restablece tu contraseña de ViaCTG",
                    "Hola " + usuario.getNombre() + ",\n\n"
                            + "Recibimos una solicitud para restablecer tu contraseña. Abre este enlace (válido "
                            + vigenciaRecuperacion.toMinutes() + " minutos y de un solo uso):\n\n" + enlace + "\n\n"
                            + "Si no fuiste tú, ignora este mensaje: tu contraseña no cambiará.");
        });
    }

    public void restablecer(String token, String passwordNueva) {
        // Antes de gastar el enlace (es de un solo uso): una contraseña inválida no debe obligar a pedir otro.
        UsuarioService.validarLongitudPassword(passwordNueva);
        TokenRecuperacion registro = tokenRepository.findByTokenHash(sha256(token))
                .filter(encontrado -> encontrado.getExpira().isAfter(Instant.now()))
                .orElseThrow(() -> new BusinessRuleException("El enlace no es válido o ya venció. Solicita uno nuevo."));
        Usuario usuario = usuarioService.buscarPorId(registro.getUsuarioId());
        tokenRepository.deleteByUsuarioId(usuario.getId());
        tokenVerificacionRepository.deleteByUsuarioId(usuario.getId());
        usuarioService.restablecerPassword(usuario.getId(), passwordNueva);
        // Quien recibió el enlace demostró ser la dueña del correo: se le quitan los bloqueos de login.
        limitador.limpiar(claveLoginEmail(usuario.getEmail()));
        limitador.limpiarConPrefijo(claveLoginEmailIp(usuario.getEmail()));
    }

    /** Cambia la contraseña y cierra las demás sesiones; devuelve el usuario para emitir un token nuevo. */
    public Usuario cambiarPassword(String usuarioId, String passwordActual, String passwordNueva) {
        UsuarioService.validarLongitudPassword(passwordNueva);
        String clave = "cambio-password:" + usuarioId;
        // Igual que en el login: se cuenta antes de comparar y se borra si la contraseña actual es correcta.
        limitador.consumir(clave, MAX_FALLOS_CAMBIO_PASSWORD, VENTANA_LOGIN);
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        if (!passwordEncoder.matches(passwordActual, usuario.getPasswordHash())) {
            throw new BusinessRuleException("La contraseña actual no es correcta");
        }
        limitador.limpiar(clave);
        return usuarioService.actualizarPassword(usuarioId, passwordNueva);
    }

    /** Invalida todos los JWT emitidos hasta ahora para este usuario. */
    public void cerrarTodasLasSesiones(String usuarioId) {
        usuarioService.incrementarVersionToken(usuarioId);
    }

    // Un solo enlace vigente por cuenta: pedir otro invalida el anterior.
    private void enviarVerificacion(Usuario usuario) {
        tokenVerificacionRepository.deleteByUsuarioId(usuario.getId());
        String token = generarToken();
        tokenVerificacionRepository.save(TokenVerificacion.builder().usuarioId(usuario.getId()).tokenHash(sha256(token))
                .expira(Instant.now().plus(vigenciaVerificacion)).build());
        String enlace = frontendUrl + "/verificar-correo?token=" + token;
        correoService.enviar(usuario.getEmail(), "Confirma tu correo en ViaCTG",
                "Hola " + usuario.getNombre() + ",\n\n"
                        + "Gracias por unirte a ViaCTG. Para activar tu cuenta, confirma tu correo abriendo este enlace (válido "
                        + vigenciaVerificacion.toHours() + " horas y de un solo uso):\n\n" + enlace + "\n\n"
                        + "Si no creaste esta cuenta, ignora este mensaje: sin confirmar no se podrá usar.");
    }

    private static String claveLoginEmail(String email) {
        return "login-email:" + email;
    }

    /** Prefijo de los fallos de un correo; la IP va al final para poder borrarlos todos con el prefijo. */
    private static String claveLoginEmailIp(String email) {
        return "login-email-ip:" + email + "|";
    }

    private String generarToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
