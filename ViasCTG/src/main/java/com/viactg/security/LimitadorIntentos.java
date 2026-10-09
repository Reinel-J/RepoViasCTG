package com.viactg.security;

import com.viactg.exception.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite de intentos en memoria por ventana deslizante. Vale para una sola instancia del backend;
 * con varias réplicas habría que moverlo a un almacén compartido (Redis, Mongo).
 */
@Component
public class LimitadorIntentos {
    private static final int LIMPIEZA_CADA = 500;

    private final Map<String, Deque<Instant>> intentos = new ConcurrentHashMap<>();
    private final Clock clock;
    private int operaciones;

    public LimitadorIntentos() {
        this(Clock.systemUTC());
    }

    LimitadorIntentos(Clock clock) {
        this.clock = clock;
    }

    /** Lanza 429 si la clave ya alcanzó el máximo dentro de la ventana; no registra nada. */
    public void verificar(String clave, int maximo, Duration ventana) {
        Instant ahora = clock.instant();
        Deque<Instant> registro = intentos.get(clave);
        if (registro == null) return;
        synchronized (registro) {
            purgar(registro, ahora.minus(ventana));
            if (registro.size() >= maximo) {
                long segundos = Duration.between(ahora, registro.peekFirst().plus(ventana)).toSeconds();
                long minutos = Math.max(1, (segundos + 59) / 60);
                throw new TooManyRequestsException("Demasiados intentos. Intenta de nuevo en " + minutos + " minuto(s).");
            }
        }
    }

    public void registrar(String clave) {
        Deque<Instant> registro = intentos.computeIfAbsent(clave, ignorada -> new ArrayDeque<>());
        synchronized (registro) {
            registro.addLast(clock.instant());
        }
        if (++operaciones % LIMPIEZA_CADA == 0) {
            limpiarVencidos(Duration.ofHours(24));
        }
    }

    /** Verifica y registra en un solo paso: para acciones que cuentan siempre, no solo al fallar. */
    public void consumir(String clave, int maximo, Duration ventana) {
        verificar(clave, maximo, ventana);
        registrar(clave);
    }

    public void limpiar(String clave) {
        intentos.remove(clave);
    }

    private void limpiarVencidos(Duration antiguedadMaxima) {
        Instant limite = clock.instant().minus(antiguedadMaxima);
        intentos.entrySet().removeIf(entrada -> {
            synchronized (entrada.getValue()) {
                purgar(entrada.getValue(), limite);
                return entrada.getValue().isEmpty();
            }
        });
    }

    private void purgar(Deque<Instant> registro, Instant limite) {
        while (!registro.isEmpty() && registro.peekFirst().isBefore(limite)) {
            registro.pollFirst();
        }
    }
}
