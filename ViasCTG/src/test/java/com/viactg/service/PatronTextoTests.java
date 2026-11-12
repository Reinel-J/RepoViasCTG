package com.viactg.service;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

// MongoDB usa PCRE; para estas expresiones (clases, \s+, ^ y $, caracteres escapados) se comporta igual que Java.
class PatronTextoTests {
    private static boolean coincide(String patron, String texto) {
        return Pattern.compile(patron, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(texto).find();
    }

    @Test
    void contieneNoDistingueTildesMayusculasNiEspaciosRepetidos() {
        String patron = PatronTexto.contiene("  INUNDACION  calle ");

        assertThat(coincide(patron, "Gran inundación   Calle 30")).isTrue();
        assertThat(coincide(patron, "Inundacion calle")).isTrue();
        assertThat(coincide(patron, "inundación en la calle")).isFalse();
        assertThat(coincide(PatronTexto.contiene("pena"), "Peña")).isTrue();
        assertThat(coincide(PatronTexto.contiene("arbol"), "ÁRBOL CAÍDO")).isTrue();
    }

    @Test
    void igualExigeElValorCompleto() {
        String patron = PatronTexto.igual("manga");

        assertThat(coincide(patron, " Manga ")).isTrue();
        assertThat(coincide(patron, "El Manga")).isFalse();
        assertThat(coincide(patron, "Mangas")).isFalse();
    }

    @Test
    void losCaracteresEspecialesSeBuscanLiteralmente() {
        String patron = PatronTexto.contiene("calle 30 (km 2.5) *$^[");

        assertThat(coincide(patron, "Hueco en la calle 30 (km 2.5) *$^[ sur")).isTrue();
        assertThat(coincide(patron, "calle 30 km 2x5")).isFalse();
        assertThat(coincide(PatronTexto.contiene(".*"), "cualquier cosa")).isFalse();
    }
}
