package com.viactg.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RecuperarPasswordRequest(@NotBlank(message = "El correo electrónico es obligatorio") @Email(message = "El correo electrónico no tiene un formato válido") String email) {
}
