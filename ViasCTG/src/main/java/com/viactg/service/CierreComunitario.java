package com.viactg.service;

import java.util.Collection;

/**
 * Regla del cierre por la comunidad: ¿bastan los votos «¿ya lo arreglaron?» para proponer cerrar un reporte en proceso?
 * Es pura (sin base de datos) para poder probarla caso por caso; {@link SeguimientoService} decide qué votos cuentan.
 *
 * @param minimoVotos      votos ponderados de «arreglado» necesarios (por defecto 3)
 * @param proporcionMinima fracción ponderada de «arreglado» sobre el total (por defecto 0.75)
 */
public record CierreComunitario(int minimoVotos, double proporcionMinima) {
    /** El voto del autor pesa el doble: es quien mejor conoce el sitio y quien lo vio dañado. */
    static final int PESO_AUTOR = 2;

    public record Voto(String usuarioId, boolean arreglado) {
    }

    /** Votos ya ponderados y si cumplen la regla. */
    public record Evaluacion(int arreglado, int sigue, boolean candidato) {
    }

    public Evaluacion evaluar(Collection<Voto> votos, String autorId) {
        int arreglado = 0;
        int sigue = 0;
        for (Voto voto : votos) {
            int peso = voto.usuarioId().equals(autorId) ? PESO_AUTOR : 1;
            if (voto.arreglado()) arreglado += peso;
            else sigue += peso;
        }
        boolean candidato = arreglado >= minimoVotos && arreglado >= proporcionMinima * (arreglado + sigue);
        return new Evaluacion(arreglado, sigue, candidato);
    }
}
