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

// Filtro de un <select>: vacío deja pasar todo; si no, compara sin mayúsculas ni tildes, igual que
// valoresUnicos, para que la opción "Manga" también muestre los reportes guardados como "manga".
export function coincideFiltro(filtro: string, valor: string | null | undefined): boolean {
  return !filtro || normalizarBusqueda(filtro) === normalizarBusqueda(valor)
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

// "1 reporte", "3 reportes": la cantidad con la palabra en singular o plural (sin el "reporte(s)" de antes).
export function plural(cantidad: number, singular: string, enPlural = `${singular}s`): string {
  return `${cantidad} ${cantidad === 1 ? singular : enPlural}`
}

// ¿Alguna palabra del texto empieza por la clave? Así "pare" encuentra "Señal de pare" pero no "reparar" ni
// "desaparecida". Compara sin tildes ni mayúsculas, y la clave puede tener varias palabras ("sin pavimentar").
export function empiezaPalabra(texto: string, clave: string): boolean {
  const normalizado = ` ${normalizarBusqueda(texto)}`
  return normalizado.includes(` ${normalizarBusqueda(clave)}`)
}
