package com.viactg.controller;

import com.viactg.dto.AuthResponse;
import com.viactg.dto.LoginRequest;
import com.viactg.dto.RecuperarPasswordRequest;
import com.viactg.dto.RestablecerPasswordRequest;
import com.viactg.dto.UsuarioRegistroRequest;
import com.viactg.mapper.UsuarioMapper;
import com.viactg.model.Usuario;
import com.viactg.security.JwtService;
import com.viactg.security.UsuarioPrincipal;
import com.viactg.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final UsuarioMapper usuarioMapper;
    private final JwtService jwtService;

    public AuthController(AuthService authService, UsuarioMapper usuarioMapper, JwtService jwtService) {
        this.authService = authService;
        this.usuarioMapper = usuarioMapper;
        this.jwtService = jwtService;
    }

    @PostMapping("/registro")
    public ResponseEntity<AuthResponse> registrar(@Valid @RequestBody UsuarioRegistroRequest request, HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta(authService.registrar(request, http.getRemoteAddr())));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return respuesta(authService.login(request.email(), request.password(), http.getRemoteAddr()));
    }

    @PostMapping("/recuperar") @ResponseStatus(HttpStatus.ACCEPTED)
    public void recuperar(@Valid @RequestBody RecuperarPasswordRequest request, HttpServletRequest http) {
        authService.solicitarRecuperacion(request.email(), http.getRemoteAddr());
    }

    @PostMapping("/restablecer") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restablecer(@Valid @RequestBody RestablecerPasswordRequest request) {
        authService.restablecer(request.token(), request.passwordNueva());
    }

    private AuthResponse respuesta(Usuario usuario) {
        return new AuthResponse(jwtService.generarToken(new UsuarioPrincipal(usuario)), "Bearer", usuarioMapper.toResponse(usuario));
    }
}
