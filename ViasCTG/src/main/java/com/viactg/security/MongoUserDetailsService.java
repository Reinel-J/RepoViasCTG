package com.viactg.security;

import com.viactg.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class MongoUserDetailsService implements UserDetailsService {
    private final UsuarioRepository usuarioRepository;
    public MongoUserDetailsService(UsuarioRepository usuarioRepository) { this.usuarioRepository = usuarioRepository; }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return usuarioRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .map(UsuarioPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
    }
}
