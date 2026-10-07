package com.viactg.dto;

public record AuthResponse(String token, String tipo, UsuarioResponse usuario) {
}
