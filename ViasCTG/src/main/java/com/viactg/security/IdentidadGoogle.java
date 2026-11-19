package com.viactg.security;

/** Datos de un ID token de Google ya verificado. {@code sub} identifica la cuenta de Google y nunca cambia. */
public record IdentidadGoogle(String sub, String email, boolean emailVerificado, String nombre) {
}
