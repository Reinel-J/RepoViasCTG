package com.viactg.service;

import com.viactg.service.CierreComunitario.Voto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CierreComunitarioTests {
    private static final String AUTOR = "autor";
    private final CierreComunitario regla = new CierreComunitario(3, 0.75);

    /** Votos de vecinos (cada uno vale 1) más, opcionalmente, el del autor (vale 2). */
    private static List<Voto> votos(int vecinosArreglado, int vecinosSigue, Boolean autorDiceArreglado) {
        List<Voto> votos = new ArrayList<>();
        for (int i = 0; i < vecinosArreglado; i++) votos.add(new Voto("vecino-a" + i, true));
        for (int i = 0; i < vecinosSigue; i++) votos.add(new Voto("vecino-s" + i, false));
        if (autorDiceArreglado != null) votos.add(new Voto(AUTOR, autorDiceArreglado));
        return votos;
    }

    // La tabla de la propuesta: vecinos «arreglado», vecinos «sigue», voto del autor (vacío si no votó).
    @ParameterizedTest(name = "{0} arreglado, {1} sigue, autor={2} → {3}-{4}, candidato={5}")
    @CsvSource({
            "0, 0, true,  2, 0, false",  // solo el autor: faltan votos
            "1, 0, true,  3, 0, true",   // el autor y un vecino
            "3, 1,     ,  3, 1, true",   // exactamente 75 %
            "3, 2,     ,  3, 2, false",  // 60 %
            "5, 0, false, 5, 2, false",  // 71 %: el desacuerdo del autor pesa
            "2, 0,     ,  2, 0, false",  // dos vecinos no bastan
            "6, 2,     ,  6, 2, true",   // 75 % con más votos
    })
    void aplicaLaRegla(int vecinosArreglado, int vecinosSigue, Boolean autor, int arreglado, int sigue, boolean candidato) {
        CierreComunitario.Evaluacion evaluacion = regla.evaluar(votos(vecinosArreglado, vecinosSigue, autor), AUTOR);

        assertThat(evaluacion.arreglado()).isEqualTo(arreglado);
        assertThat(evaluacion.sigue()).isEqualTo(sigue);
        assertThat(evaluacion.candidato()).isEqualTo(candidato);
    }

    @Test
    void sinVotosNoHayCandidatura() {
        assertThat(regla.evaluar(List.of(), AUTOR).candidato()).isFalse();
    }

    @Test
    void losUmbralesSonConfigurables() {
        CierreComunitario estricta = new CierreComunitario(5, 0.9);

        assertThat(estricta.evaluar(votos(4, 0, null), AUTOR).candidato()).isFalse();
        assertThat(estricta.evaluar(votos(9, 1, null), AUTOR).candidato()).isTrue();
    }
}
