package com.viactg.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UsuarioRegistroRequest(
        @NotBlank(message = "El nombre es obligatorio") @Size(max = 100, message = "El nombre admite máximo 100 caracteres") String nombre,
        @NotBlank(message = "El correo electrónico es obligatorio") @Email(message = "El correo electrónico no tiene un formato válido")
        @Size(max = 150, message = "El correo electrónico admite máximo 150 caracteres") String email,
        @NotBlank(message = "La contraseña es obligatoria") @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres") String password,
        @Pattern(regexp = "^[0-9+() -]{7,25}$", message = "El teléfono no tiene un formato válido") String telefono
) {
}
