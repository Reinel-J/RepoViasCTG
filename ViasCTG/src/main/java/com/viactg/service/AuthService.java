package com.viactg.service;

import com.viactg.dto.UsuarioRegistroRequest;
import com.viactg.exception.BusinessRuleException;
import com.viactg.model.TokenRecuperacion;
import com.viactg.model.Usuario;
import com.viactg.repository.TokenRecuperacionRepository;
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

/** Autenticación y ciclo de vida de credenciales: login, registro, contraseña y revocación de sesiones. */
@Service
public class AuthService {
    private static final Duration VENTANA_LOGIN = Duration.ofMinutes(15);
    private static final int MAX_LOGIN_POR_IP = 20;
    private static final int MAX_FALLOS_POR_EMAIL = 5;
    private static final Duration VENTANA_HORA = Duration.ofHours(1);
    private static final int MAX_REGISTROS_POR_IP = 5;
    private static final int MAX_RECUPERACIONES_POR_IP = 5;
    private static final int MAX_RECUPERACIONES_POR_EMAIL = 3;
    private static final int MAX_FALLOS_CAMBIO_PASSWORD = 5;
    private static final String CREDENCIALES_INVALIDAS = "Credenciales inválidas o usuario inactivo";

    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final TokenRecuperacionRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final LimitadorIntentos limitador;
    private final CorreoService correoService;
    private final String frontendUrl;
    private final Duration vigenciaRecuperacion;
    private final SecureRandom random = new SecureRandom();
    // Hash ficticio para que un correo inexistente cueste lo mismo que una contraseña incorrecta.
    private final String hashFicticio;

    public AuthService(UsuarioRepository usuarioRepository, UsuarioService usuarioService, TokenRecuperacionRepository tokenRepository,
                       PasswordEncoder passwordEncoder, LimitadorIntentos limitador, CorreoService correoService,
                       @Value("${app.frontend.url}") String frontendUrl,
                       @Value("${app.password-reset.expiration-minutes:30}") long minutosRecuperacion) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.limitador = limitador;
        this.correoService = correoService;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.vigenciaRecuperacion = Duration.ofMinutes(minutosRecuperacion);
        this.hashFicticio = passwordEncoder.encode("contraseña-ficticia-para-igualar-tiempos");
    }

    public Usuario login(String email, String password, String ip) {
        String emailNormalizado = normalizar(email);
        String claveIp = "login-ip:" + ip;
        String claveEmail = "login-email:" + emailNormalizado;
        limitador.verificar(claveIp, MAX_LOGIN_POR_IP, VENTANA_LOGIN);
        limitador.verificar(claveEmail, MAX_FALLOS_POR_EMAIL, VENTANA_LOGIN);
        limitador.registrar(claveIp);

        Optional<Usuario> usuario = usuarioRepository.findByEmail(emailNormalizado);
        boolean coincide = passwordEncoder.matches(password, usuario.map(Usuario::getPasswordHash).orElse(hashFicticio));
        if (usuario.isEmpty() || !coincide || !usuario.get().isActivo()) {
            limitador.registrar(claveEmail);
            throw new BadCredentialsException(CREDENCIALES_INVALIDAS);
        }
        limitador.limpiar(claveEmail);
        return usuario.get();
    }

    public Usuario registrar(UsuarioRegistroRequest request, String ip) {
        limitador.consumir("registro-ip:" + ip, MAX_REGISTROS_POR_IP, VENTANA_HORA);
        return usuarioService.registrar(request);
    }

    /** Siempre termina igual exista o no el correo, para no revelar qué cuentas hay. */
    public void solicitarRecuperacion(String email, String ip) {
        limitador.consumir("recuperar-ip:" + ip, MAX_RECUPERACIONES_POR_IP, VENTANA_HORA);
        String emailNormalizado = normalizar(email);
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
        TokenRecuperacion registro = tokenRepository.findByTokenHash(sha256(token))
                .filter(encontrado -> encontrado.getExpira().isAfter(Instant.now()))
                .orElseThrow(() -> new BusinessRuleException("El enlace no es válido o ya venció. Solicita uno nuevo."));
        Usuario usuario = usuarioService.buscarPorId(registro.getUsuarioId());
        tokenRepository.deleteByUsuarioId(usuario.getId());
        actualizarPassword(usuario, passwordNueva);
        limitador.limpiar("login-email:" + usuario.getEmail());
    }

    /** Cambia la contraseña y cierra las demás sesiones; devuelve el usuario para emitir un token nuevo. */
    public Usuario cambiarPassword(String usuarioId, String passwordActual, String passwordNueva) {
        String clave = "cambio-password:" + usuarioId;
        limitador.verificar(clave, MAX_FALLOS_CAMBIO_PASSWORD, VENTANA_LOGIN);
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        if (!passwordEncoder.matches(passwordActual, usuario.getPasswordHash())) {
            limitador.registrar(clave);
            throw new BusinessRuleException("La contraseña actual no es correcta");
        }
        limitador.limpiar(clave);
        return actualizarPassword(usuario, passwordNueva);
    }

    /** Invalida todos los JWT emitidos hasta ahora para este usuario. */
    public void cerrarTodasLasSesiones(String usuarioId) {
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        usuario.setVersionToken(usuario.getVersionToken() + 1);
        usuarioRepository.save(usuario);
    }

    private Usuario actualizarPassword(Usuario usuario, String passwordNueva) {
        usuario.setPasswordHash(passwordEncoder.encode(passwordNueva));
        usuario.setVersionToken(usuario.getVersionToken() + 1);
        return usuarioRepository.save(usuario);
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

    private String normalizar(String email) {
        return email.trim().toLowerCase();
    }
}
