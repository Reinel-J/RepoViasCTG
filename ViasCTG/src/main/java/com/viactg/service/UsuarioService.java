package com.viactg.service;

import com.viactg.dto.UsuarioActualizacionRequest;
import com.viactg.dto.UsuarioRegistroRequest;
import com.viactg.exception.DuplicateResourceException;
import com.viactg.exception.ResourceNotFoundException;
import com.viactg.model.Rol;
import com.viactg.model.Usuario;
import com.viactg.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class UsuarioService {
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Usuario registrar(UsuarioRegistroRequest request) {
        String email = request.email().trim().toLowerCase();
        if (usuarioRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Ya existe un usuario con ese email");
        }
        Usuario usuario = Usuario.builder().nombre(request.nombre().trim()).email(email)
                .passwordHash(passwordEncoder.encode(request.password())).telefono(request.telefono())
                .rol(Rol.CIUDADANO).activo(true).fechaRegistro(Instant.now()).build();
        return usuarioRepository.save(usuario);
    }

    public Usuario buscarPorId(String id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + id));
    }

    public Usuario buscarPorEmail(String email) {
        return usuarioRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    public List<Usuario> listar() {
        return usuarioRepository.findAll();
    }

    public Usuario actualizarPerfil(String id, UsuarioActualizacionRequest request) {
        Usuario usuario = buscarPorId(id);
        if (request.nombre() != null) usuario.setNombre(request.nombre().trim());
        if (request.telefono() != null) usuario.setTelefono(request.telefono());
        return usuarioRepository.save(usuario);
    }

    public String nombrePorId(String id) {
        return usuarioRepository.findById(id).map(Usuario::getNombre).orElse(null);
    }

    public java.util.Map<String, String> nombresPorId(java.util.Collection<String> ids) {
        java.util.Map<String, String> nombres = new java.util.HashMap<>();
        usuarioRepository.findAllById(ids).forEach(usuario -> nombres.put(usuario.getId(), usuario.getNombre()));
        return nombres;
    }

    public boolean esAdministradorOModerador(String id) {
        Rol rol = buscarPorId(id).getRol();
        return rol == Rol.ADMIN || rol == Rol.MODERADOR;
    }
}
