// Normaliza un texto para buscar sin distinguir mayúsculas, tildes ni espacios repetidos
// ("Inundación" y "inundacion" coinciden).
export function normalizarBusqueda(texto: string | null | undefined): string {
  return (texto ?? "")
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .toLowerCase()
    .replace(/\s+/g, " ")
    .trim()
}

export function coincideBusqueda(busqueda: string, ...campos: (string | null | undefined)[]): boolean {
  const termino = normalizarBusqueda(busqueda)
  if (!termino) return true
  return campos.some((campo) => normalizarBusqueda(campo).includes(termino))
}

// Valores distintos y no vacíos, ordenados alfabéticamente en español.
export function valoresUnicos(valores: (string | null | undefined)[]): string[] {
  const vistos = new Map<string, string>()
  for (const valor of valores) {
    const limpio = valor?.trim()
    if (!limpio) continue
    const clave = normalizarBusqueda(limpio)
    if (!vistos.has(clave)) vistos.set(clave, limpio)
  }
  return [...vistos.values()].sort((a, b) => a.localeCompare(b, "es"))
}
