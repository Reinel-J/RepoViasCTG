package com.viactg.service;

import com.viactg.exception.ForbiddenOperationException;
import com.viactg.model.Usuario;
import com.viactg.repository.TokenVerificacionRepository;
import com.viactg.security.IdentidadGoogle;
import com.viactg.security.LimitadorIntentos;
import com.viactg.security.VerificadorGoogle;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** «Iniciar sesión con Google»: verifica el ID token y devuelve la cuenta de ViaCTG (vinculada o nueva). */
@Service
public class AccesoGoogleService {
    private static final Duration VENTANA = Duration.ofMinutes(15);
    // Igual que el login con contraseña: cada intento cuesta verificar una firma y consultar la base.
    private static final int MAX_POR_IP = 20;

    private final VerificadorGoogle verificador;
    private final UsuarioService usuarioService;
    private final TokenVerificacionRepository tokenVerificacionRepository;
    private final LimitadorIntentos limitador;

    public AccesoGoogleService(VerificadorGoogle verificador, UsuarioService usuarioService,
                               TokenVerificacionRepository tokenVerificacionRepository, LimitadorIntentos limitador) {
        this.verificador = verificador;
        this.usuarioService = usuarioService;
        this.tokenVerificacionRepository = tokenVerificacionRepository;
        this.limitador = limitador;
    }

    /** Client ID para el botón del frontend; null si el inicio de sesión con Google no está configurado. */
    public String clientId() {
        return verificador.clientId();
    }

    public Usuario acceder(String credencial, String ip) {
        limitador.consumir("google-ip:" + ip, MAX_POR_IP, VENTANA);
        IdentidadGoogle identidad = verificador.verificar(credencial);
        // Sin esto, cualquiera podría crear una cuenta de Google con un correo ajeno (sin verificar) y entrar a la
        // cuenta de ViaCTG de esa persona.
        if (!identidad.emailVerificado()) {
            throw new ForbiddenOperationException("Tu cuenta de Google no tiene el correo verificado. Verifícalo en Google "
                    + "o crea una cuenta en ViaCTG con correo y contraseña.");
        }
        Usuario usuario = usuarioService.accederConGoogle(identidad.sub(), UsuarioService.normalizarEmail(identidad.email()),
                identidad.nombre());
        // Si la cuenta estaba sin confirmar, Google acaba de confirmar el correo: el enlace pendiente sobra.
        tokenVerificacionRepository.deleteByUsuarioId(usuario.getId());
        return usuario;
    }
}
