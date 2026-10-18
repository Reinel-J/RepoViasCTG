interface DatosCompartir {
  categoria: string
  direccionOsm: string | null
  estado: string
  cerrado: boolean
}

// Texto para compartir un reporte, sin la URL: el menú nativo del teléfono la recibe aparte y la agrega él mismo.
// Sin dirección de OSM se dice "Cartagena" en lugar de mostrar coordenadas, que en un chat no le dicen nada a nadie.
export function textoCompartir({ categoria, direccionOsm, estado, cerrado }: DatosCompartir): string {
  const llamado = cerrado ? "Míralo en VíaCTG:" : "¿Lo has visto? Confírmalo en VíaCTG para darle más fuerza:"
  return `${categoria} en ${direccionOsm ?? "Cartagena"} (estado: ${estado.toLowerCase()}). ${llamado}`
}

export function enlaceWhatsApp(texto: string, url: string): string {
  return `https://wa.me/?text=${encodeURIComponent(`${texto} ${url}`)}`
}
