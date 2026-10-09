package com.viactg.controller;

import com.viactg.dto.AuthResponse;
import com.viactg.dto.LoginRequest;
import com.viactg.dto.UsuarioRegistroRequest;
import com.viactg.mapper.UsuarioMapper;
import com.viactg.model.Usuario;
import com.viactg.security.JwtService;
import com.viactg.security.UsuarioPrincipal;
import com.viactg.service.UsuarioService;
import jakarta.validation.Valid;
import com.viactg.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UsuarioService usuarioService;
    private final UsuarioMapper usuarioMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UsuarioService usuarioService, UsuarioMapper usuarioMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.usuarioService = usuarioService;
        this.usuarioMapper = usuarioMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/registro")
    public ResponseEntity<AuthResponse> registrar(@Valid @RequestBody UsuarioRegistroRequest request) {
        Usuario usuario = usuarioService.registrar(request);
        UsuarioPrincipal principal = new UsuarioPrincipal(usuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(jwtService.generarToken(principal), "Bearer", usuarioMapper.toResponse(usuario)));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        Usuario usuario;
        try {
            usuario = usuarioService.buscarPorEmail(request.email());
        } catch (ResourceNotFoundException exception) {
            // Misma respuesta que una contraseña incorrecta para no revelar qué correos existen.
            throw new BadCredentialsException("Credenciales inválidas o usuario inactivo");
        }
        if (!usuario.isActivo() || !passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            throw new BadCredentialsException("Credenciales inválidas o usuario inactivo");
        }
        UsuarioPrincipal principal = new UsuarioPrincipal(usuario);
        return new AuthResponse(jwtService.generarToken(principal), "Bearer", usuarioMapper.toResponse(usuario));
    }
}
