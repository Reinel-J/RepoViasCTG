package com.viactg.config;

import com.viactg.service.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Garantiza un administrador al arrancar si se definen ADMIN_EMAIL y ADMIN_PASSWORD. */
@Component
public class AdminInicial implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminInicial.class);
    private final UsuarioService usuarioService;
    private final String email;
    private final String password;

    public AdminInicial(UsuarioService usuarioService, @Value("${app.admin.email:}") String email,
                        @Value("${app.admin.password:}") String password) {
        this.usuarioService = usuarioService;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) return;
        if (password.length() < 8) {
            log.warn("ADMIN_PASSWORD debe tener al menos 8 caracteres; no se creó el administrador inicial");
            return;
        }
        usuarioService.asegurarAdministrador(email, password, "Administrador");
        log.info("Administrador inicial asegurado: {}", email);
    }
}
