package com.viactg.security;

import com.viactg.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void limpiarConPrefijoBorraSoloLasClavesQueEmpiezanAsi() {
        LimitadorIntentos limitador = new LimitadorIntentos(new RelojMovil());
        limitador.registrar("login-email-ip:ana@test.com|1.1.1.1");
        limitador.registrar("login-email-ip:ana@test.com|2.2.2.2");
        limitador.registrar("login-email-ip:ana@test.com.co|1.1.1.1");

        limitador.limpiarConPrefijo("login-email-ip:ana@test.com|");

        assertThatCode(() -> limitador.verificar("login-email-ip:ana@test.com|1.1.1.1", 1, Duration.ofMinutes(1))).doesNotThrowAnyException();
        assertThatCode(() -> limitador.verificar("login-email-ip:ana@test.com|2.2.2.2", 1, Duration.ofMinutes(1))).doesNotThrowAnyException();
        assertThatThrownBy(() -> limitador.verificar("login-email-ip:ana@test.com.co|1.1.1.1", 1, Duration.ofMinutes(1)))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void lasPeticionesSimultaneasNoSuperanElMaximo() throws Exception {
        LimitadorIntentos limitador = new LimitadorIntentos();
        int hilos = 64;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();
        try {
            for (int i = 0; i < hilos; i++) {
                resultados.add(pool.submit(() -> {
                    salida.await();
                    try {
                        limitador.consumir("login-email:ana@test.com", 5, Duration.ofMinutes(15));
                        return true;
                    } catch (TooManyRequestsException rechazado) {
                        return false;
                    }
                }));
            }
            salida.countDown();
            long aceptadas = 0;
            for (Future<Boolean> resultado : resultados) {
                if (resultado.get(10, TimeUnit.SECONDS)) aceptadas++;
            }
            assertThat(aceptadas).isEqualTo(5);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void verificarNoCuentaComoIntento() {
        LimitadorIntentos limitador = new LimitadorIntentos(new RelojMovil());
        for (int i = 0; i < 10; i++) limitador.verificar("clave", 1, Duration.ofMinutes(1));
        assertThatCode(() -> limitador.consumir("clave", 1, Duration.ofMinutes(1))).doesNotThrowAnyException();
        assertThatThrownBy(() -> limitador.consumir("clave", 1, Duration.ofMinutes(1))).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void lasClavesVencidasNoSeAcumulanEnMemoria() {
        RelojMovil reloj = new RelojMovil();
        LimitadorIntentos limitador = new LimitadorIntentos(reloj);
        for (int i = 0; i < 499; i++) limitador.consumir("registro-ip:10.0.0." + i, 5, Duration.ofHours(1));
        reloj.ahora = reloj.ahora.plus(Duration.ofHours(2));
        // La operación 500 dispara la limpieza: solo queda la clave recién usada.
        limitador.consumir("registro-ip:otra", 5, Duration.ofHours(1));
        assertThat(limitador.clavesRegistradas()).isEqualTo(1);
    }
}
