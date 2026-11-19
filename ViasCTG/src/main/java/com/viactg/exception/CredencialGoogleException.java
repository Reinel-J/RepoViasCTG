package com.viactg.exception;

/** ID token de Google inválido o vencido (401). Aparte de BadCredentialsException, cuyo mensaje habla de contraseñas. */
public class CredencialGoogleException extends RuntimeException {
    public CredencialGoogleException(String message) {
        super(message);
    }
}
