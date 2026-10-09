package com.viactg.security;

import com.viactg.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimitadorIntentosTests {

    static class RelojMovil extends Clock {
        Instant ahora = Instant.parse("2026-01-01T00:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
    }

    @Test
    void bloqueaAlAlcanzarElMaximoYSeLiberaAlPasarLaVentana() {
        RelojMovil reloj = new RelojMovil();
        LimitadorIntentos limitador = new LimitadorIntentos(reloj);
        for (int i = 0; i < 3; i++) limitador.consumir("clave", 3, Duration.ofMinutes(15));

        assertThatThrownBy(() -> limitador.consumir("clave", 3, Duration.ofMinutes(15)))
                .isInstanceOf(TooManyRequestsException.class).hasMessageContaining("15 minuto");

        reloj.ahora = reloj.ahora.plus(Duration.ofMinutes(16));
        assertThatCode(() -> limitador.consumir("clave", 3, Duration.ofMinutes(15))).doesNotThrowAnyException();
    }

    @Test
    void limpiarReiniciaElContadorYLasClavesSonIndependientes() {
        LimitadorIntentos limitador = new LimitadorIntentos(new RelojMovil());
        limitador.registrar("a");
        limitador.registrar("a");
        assertThatThrownBy(() -> limitador.verificar("a", 2, Duration.ofMinutes(1))).isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> limitador.verificar("b", 2, Duration.ofMinutes(1))).doesNotThrowAnyException();

        limitador.limpiar("a");
        assertThatCode(() -> limitador.verificar("a", 2, Duration.ofMinutes(1))).doesNotThrowAnyException();
    }
}
