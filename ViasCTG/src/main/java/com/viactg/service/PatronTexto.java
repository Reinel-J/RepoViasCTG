package com.viactg.service;

/**
 * Expresiones regulares de MongoDB que comparan texto sin distinguir mayúsculas, tildes ni espacios repetidos, como
 * los filtros del frontend ({@code utils/texto.ts}). MongoDB no tiene un regex insensible a tildes: el texto buscado
 * se lleva a su forma sin tildes y cada vocal (y la n, la c) se convierte en una clase con sus variantes, así
 * "inundacion" encuentra "Inundación". Todo lo que no es letra ni dígito se escapa, de modo que la búsqueda es
 * literal y la expresión no puede tener retrocesos costosos.
 */
final class PatronTexto {
    private PatronTexto() {
    }

    /** Coincide si el campo contiene el texto. */
    static String contiene(String texto) {
        return expresion(texto);
    }

    /** Coincide si el campo es el texto (salvo espacios al inicio y al final). */
    static String igual(String texto) {
        return "^\\s*" + expresion(texto) + "\\s*$";
    }

    private static String expresion(String texto) {
        String clave = CategoriaService.claveComparacion(texto);
        StringBuilder patron = new StringBuilder();
        clave.codePoints().forEach(caracter -> patron.append(fragmento(caracter)));
        return patron.toString();
    }

    private static String fragmento(int caracter) {
        return switch (caracter) {
            case 'a' -> "[aáàâäAÁÀÂÄ]";
            case 'e' -> "[eéèêëEÉÈÊË]";
            case 'i' -> "[iíìîïIÍÌÎÏ]";
            case 'o' -> "[oóòôöOÓÒÔÖ]";
            case 'u' -> "[uúùûüUÚÙÛÜ]";
            case 'n' -> "[nñNÑ]";
            case 'c' -> "[cçCÇ]";
            case ' ' -> "\\s+";
            default -> Character.isLetterOrDigit(caracter) ? Character.toString(caracter) : "\\" + Character.toString(caracter);
        };
    }
}
