package com.viactg.service;

import com.viactg.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AreaServicioTests {
    private final AreaServicio cartagena = AreaServicio.cartagena();

    @Test
    void aceptaPuntosDeCartagenaYSusAlrededores() {
        assertThat(cartagena.contiene(10.4236, -75.5478)).isTrue(); // Centro histórico
        assertThat(cartagena.contiene(10.3910, -75.4790)).isTrue(); // Sur occidente
        assertThat(cartagena.contiene(10.1800, -75.7500)).isTrue(); // Islas del Rosario
    }

    @Test
    void rechazaPuntosDeOtrasCiudades() {
        assertThat(cartagena.contiene(4.7110, -74.0721)).isFalse(); // Bogotá
        assertThat(cartagena.contiene(10.9685, -74.7813)).isFalse(); // Barranquilla
        assertThatThrownBy(() -> cartagena.validar(0, 0)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cartagena");
    }

    @Test
    void sePuedeDesactivar() {
        AreaServicio libre = new AreaServicio(false, "Cartagena", 10.15, 10.65, -75.80, -75.30);
        assertThatCode(() -> libre.validar(4.7110, -74.0721)).doesNotThrowAnyException();
    }
}
