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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Límite de intentos en memoria por ventana deslizante. Vale para una sola instancia del backend;
 * con varias réplicas habría que moverlo a un almacén compartido (Redis, Mongo).
 *
 * <p>Cada registro solo se toca dentro de {@code compute}/{@code computeIfPresent} del mapa, que son atómicos
 * por clave: dos peticiones simultáneas nunca leen el mismo conteo, así que no se puede superar el máximo
 * enviando intentos en paralelo. Las ventanas que se usen no deben superar {@link #RETENCION}.
 */
@Component
public class LimitadorIntentos {
    private static final int LIMPIEZA_CADA = 500;
    static final Duration RETENCION = Duration.ofHours(1);

    private final Map<String, Deque<Instant>> intentos = new ConcurrentHashMap<>();
    private final Clock clock;
    private final AtomicInteger operaciones = new AtomicInteger();

    public LimitadorIntentos() {
        this(Clock.systemUTC());
    }

    LimitadorIntentos(Clock clock) {
        this.clock = clock;
    }

    /** Lanza 429 si la clave ya alcanzó el máximo dentro de la ventana; no registra nada. */
    public void verificar(String clave, int maximo, Duration ventana) {
        Instant ahora = clock.instant();
        long[] segundosEspera = {-1};
        intentos.computeIfPresent(clave, (ignorada, registro) -> {
            purgar(registro, ahora.minus(ventana));
            if (registro.size() >= maximo) segundosEspera[0] = segundosHastaLiberar(registro, ahora, ventana);
            return registro.isEmpty() ? null : registro;
        });
        if (segundosEspera[0] >= 0) throw demasiadosIntentos(segundosEspera[0]);
    }

    public void registrar(String clave) {
        Instant ahora = clock.instant();
        intentos.compute(clave, (ignorada, registro) -> {
            Deque<Instant> actual = registro == null ? new ArrayDeque<>() : registro;
            actual.addLast(ahora);
            return actual;
        });
        limpiarSiCorresponde();
    }

    /**
     * Verifica y registra en un solo paso atómico. Sirve también para contar fallos sin carreras: se consume el
     * intento antes de comprobar la credencial y se llama a {@link #limpiar} si resulta correcta.
     */
    public void consumir(String clave, int maximo, Duration ventana) {
        Instant ahora = clock.instant();
        long[] segundosEspera = {-1};
        intentos.compute(clave, (ignorada, registro) -> {
            Deque<Instant> actual = registro == null ? new ArrayDeque<>() : registro;
            purgar(actual, ahora.minus(ventana));
            if (actual.size() >= maximo) {
                segundosEspera[0] = segundosHastaLiberar(actual, ahora, ventana);
            } else {
                actual.addLast(ahora);
            }
            return actual.isEmpty() ? null : actual;
        });
        limpiarSiCorresponde();
        if (segundosEspera[0] >= 0) throw demasiadosIntentos(segundosEspera[0]);
    }

    public void limpiar(String clave) {
        intentos.remove(clave);
    }

    /** Borra todas las claves que empiezan por {@code prefijo} (por ejemplo, los fallos de un correo desde cualquier IP). */
    public void limpiarConPrefijo(String prefijo) {
        intentos.keySet().removeIf(clave -> clave.startsWith(prefijo));
    }

    int clavesRegistradas() {
        return intentos.size();
    }

    private void limpiarSiCorresponde() {
        if (operaciones.incrementAndGet() % LIMPIEZA_CADA != 0) return;
        Instant limite = clock.instant().minus(RETENCION);
        for (String clave : intentos.keySet()) {
            intentos.computeIfPresent(clave, (ignorada, registro) -> {
                purgar(registro, limite);
                return registro.isEmpty() ? null : registro;
            });
        }
    }

    private static long segundosHastaLiberar(Deque<Instant> registro, Instant ahora, Duration ventana) {
        return Math.max(0, Duration.between(ahora, registro.peekFirst().plus(ventana)).toSeconds());
    }

    private static TooManyRequestsException demasiadosIntentos(long segundos) {
        long minutos = Math.max(1, (segundos + 59) / 60);
        return new TooManyRequestsException("Demasiados intentos. Intenta de nuevo en " + minutos + (minutos == 1 ? " minuto." : " minutos."));
    }

    private static void purgar(Deque<Instant> registro, Instant limite) {
        while (!registro.isEmpty() && registro.peekFirst().isBefore(limite)) {
            registro.pollFirst();
        }
    }
}
