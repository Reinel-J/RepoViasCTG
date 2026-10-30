package com.viactg.exception;

/** Una función que depende de un servicio externo sin configurar (por ejemplo, el dashboard de Metabase): 503. */
public class ServicioNoDisponibleException extends RuntimeException {
    public ServicioNoDisponibleException(String message) {
        super(message);
    }
}
