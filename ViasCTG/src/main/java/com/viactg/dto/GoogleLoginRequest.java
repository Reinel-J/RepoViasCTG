package com.viactg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code credencial}: el ID token (JWT) que Google Identity Services entrega al navegador. */
public record GoogleLoginRequest(@NotBlank(message = "Falta la credencial de Google")
                                 @Size(max = 4096, message = "La credencial de Google no es válida") String credencial) {
}
