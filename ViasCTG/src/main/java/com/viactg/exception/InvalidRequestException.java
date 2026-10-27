package com.viactg.exception;

/** Dato de entrada inválido que no se puede expresar con anotaciones de validación en el DTO: responde 400. */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
