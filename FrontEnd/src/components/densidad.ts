// Rampa secuencial de un solo tono para el mapa de densidad: más reportes = rojo más intenso, como en un mapa de
// calor. Los pasos tienen la misma luminosidad creciente que la rampa azul anterior, y el más claro mantiene un
// contraste de al menos 2:1 con el fondo del mapa de OpenStreetMap.
export const RAMPA_DENSIDAD = ['#fb7f73', '#e04840', '#b3161a', '#750a0d']

export function colorDensidad(total: number, maximo: number) {
  const indice = Math.ceil((total / Math.max(1, maximo)) * RAMPA_DENSIDAD.length) - 1
  return RAMPA_DENSIDAD[Math.min(RAMPA_DENSIDAD.length - 1, Math.max(0, indice))]
}
