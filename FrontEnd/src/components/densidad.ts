// Rampa secuencial de un solo tono para el mapa de densidad: más reportes = azul más oscuro.
export const RAMPA_DENSIDAD = ['#86b6ef', '#3987e5', '#1c5cab', '#0d366b']

export function colorDensidad(total: number, maximo: number) {
  const indice = Math.ceil((total / Math.max(1, maximo)) * RAMPA_DENSIDAD.length) - 1
  return RAMPA_DENSIDAD[Math.min(RAMPA_DENSIDAD.length - 1, Math.max(0, indice))]
}
